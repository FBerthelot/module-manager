/**
 * ==========================================================================================
 * =                   JAHIA'S DUAL LICENSING - IMPORTANT INFORMATION                       =
 * ==========================================================================================
 *
 *                                 http://www.jahia.com
 *
 *     Copyright (C) 2002-2016 Jahia Solutions Group SA. All rights reserved.
 *
 *     THIS FILE IS AVAILABLE UNDER TWO DIFFERENT LICENSES:
 *     1/GPL OR 2/JSEL
 *
 *     1/ GPL
 *     ==================================================================================
 *
 *     IF YOU DECIDE TO CHOOSE THE GPL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 *
 *     2/ JSEL - Commercial and Supported Versions of the program
 *     ===================================================================================
 *
 *     IF YOU DECIDE TO CHOOSE THE JSEL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     Alternatively, commercial and supported versions of the program - also known as
 *     Enterprise Distributions - must be used in accordance with the terms and conditions
 *     contained in a separate written agreement between you and Jahia Solutions Group SA.
 *
 *     If you are unsure which license is appropriate for your use,
 *     please contact the sales department at sales@jahia.com.
 */
package org.jahia.modules.modulemanager.endpoint;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.jahia.modules.modulemanager.exception.MissingBundleKeyValueException;
import org.jahia.modules.modulemanager.exception.ModuleDeploymentException;
import org.jahia.services.modulemanager.ModuleManagementException;
import org.jahia.services.modulemanager.ModuleManager;
import org.jahia.services.modulemanager.OperationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

/**
 * The REST service implementation for module manager API.
 * 
 * @author bdjiba
 */
public class ModuleManagerResource implements ModuleManagerSpi {
  
  private static final Logger log = LoggerFactory.getLogger(ModuleManagerResource.class);
  
  private ModuleManager moduleManager;
  
  private Resource getUploadedFileAsResource(InputStream uploadedFileIs, String filename) throws ModuleDeploymentException {
    // create internal temp file
    FileOutputStream fileOutputStream = null;
    File tempFile = null;
    try {
        tempFile = File.createTempFile(FilenameUtils.getBaseName(filename) + "-", "." + FilenameUtils.getExtension(filename), FileUtils.getTempDirectory());
        fileOutputStream = new FileOutputStream(tempFile);
        IOUtils.copy(uploadedFileIs, fileOutputStream);
    } catch (IOException ioex) {
        log.error("Error copy uploaded stream to local temp file for " + filename, ioex);
        throw new ModuleDeploymentException(Response.Status.INTERNAL_SERVER_ERROR, "Error while deploying bundle " + filename, ioex);
    } finally {
        IOUtils.closeQuietly(fileOutputStream);
        IOUtils.closeQuietly(uploadedFileIs);
    }
    // set the file as resource to forward
    Resource bundleResource = new FileSystemResource(tempFile);
    return bundleResource;
  }
  
  private void validateBundleOperation(String bundleKey, String serviceOperation) throws MissingBundleKeyValueException {
    if(StringUtils.isBlank(bundleKey)) {
      throw new MissingBundleKeyValueException("Bundle key is mandatory for " + serviceOperation + " operation.");
    }
  }

  @Override
  public Response install(InputStream bundleFileInputStream, FormDataContentDisposition fileDisposition, FormDataBodyPart fileBodyPart, ClusterNodesMultiPartParam nodes) throws ModuleDeploymentException {
    if(bundleFileInputStream == null || fileDisposition == null || StringUtils.isEmpty(fileDisposition.getFileName())) {
      throw new ModuleDeploymentException(Response.Status.BAD_REQUEST, "The bundle file could not be null");
    }
    
    Resource bundleResource = null;
    if(log.isDebugEnabled()) {
      log.debug("Installing bundle {} on nodes {}", fileDisposition.getFileName(), nodes);
    }
    try{
      bundleResource = getUploadedFileAsResource(bundleFileInputStream, fileDisposition.getFileName());
      
      try {
          // FIXME: from rest, always force the update then we will not check force update
          OperationResult result = getModuleManager().install(bundleResource, nodes.getNodeIds().toString());
          
          return Response.ok(result).build();
      } catch (ModuleManagementException ex) {
        log.error("Module management exception when installing module.", ex);
        throw new ModuleDeploymentException(Response.Status.EXPECTATION_FAILED, ex.getMessage(), ex);
      } catch (Exception bex) {
        log.error("An Exception occured during module installation.", bex.getMessage(), bex);
        throw new ModuleDeploymentException(Response.Status.EXPECTATION_FAILED, bex.getMessage(), bex);
      }
    }finally {
      if(bundleResource != null) {
        try{
          File bundleFile = bundleResource.getFile();
          FileUtils.deleteQuietly(bundleFile);
        } catch(IOException ioex){
          log.trace("Unable to clean installed bundle file", ioex);
        }
      }
    }
  }

  @Override
  public Response uninstall(String bundleKey, ClusterNodesPostParam nodes) throws ModuleDeploymentException {
    validateBundleOperation(bundleKey, "uninstall");
    if(log.isDebugEnabled()) {
      log.debug("Uninstall bundle {}  on nodes {}", bundleKey, nodes);
    }
    try{
      OperationResult result = getModuleManager().uninstall(bundleKey, nodes.getNodeIds().toString());
      return Response.ok(result).build();      
    } catch(ModuleManagementException mmEx){
      log.error("Error while uninstalling module " + bundleKey, mmEx);
      throw new ModuleDeploymentException(Status.INTERNAL_SERVER_ERROR, mmEx.getMessage(), mmEx);
    }
  }

  @Override
  public Response start(String bundleKey, ClusterNodesPostParam nodes) throws ModuleDeploymentException {
    validateBundleOperation(bundleKey, "start");
    if(log.isDebugEnabled()) {
      log.debug("Start bundle {} on nodes {}", bundleKey, nodes);
    }
    
    try{
      OperationResult result = getModuleManager().start(bundleKey, nodes.getNodeIds().toString());
      return Response.ok(result).build();      
    } catch(ModuleManagementException mmEx){
      log.error("Error while starting bundle " + bundleKey, mmEx);
      throw new ModuleDeploymentException(Status.INTERNAL_SERVER_ERROR, mmEx.getMessage());
    }
  }

  @Override
  public Response stop(String bundleKey, ClusterNodesPostParam nodes) throws ModuleDeploymentException {
    validateBundleOperation(bundleKey, "stop");
    if(log.isDebugEnabled()) {
      log.debug("Stoping bundle {} on nodes {}", bundleKey, nodes);
    }
    try{
      OperationResult result = getModuleManager().stop(bundleKey, nodes.getNodeIds().toString());
      return Response.ok(result).build();      
    } catch(ModuleManagementException mmEx){
      log.error("Error while stoping module.", mmEx);
      throw new ModuleDeploymentException(Status.INTERNAL_SERVER_ERROR, mmEx.getMessage());
    }
  }

  @Override
  public Response getBundleState(String bundleUniqueKey, ClusterNodesGetParam nodes) throws ModuleDeploymentException {
    if(log.isDebugEnabled()) {
      log.debug("Get bundle state {}", bundleUniqueKey);
    }
//    return Response.ok(getModuleManager().getBundleState(bundleUniqueKey, nodes.getNodeIds())).build();
    return null;
  }

  @Override
  public Response getNodesBundleStates(ClusterNodesGetParam nodes) throws ModuleDeploymentException {
    if(log.isDebugEnabled()) {
      log.debug("Get bundle states for nodes {}", nodes);
    }
//    return Response.ok(getModuleManager().getNodesBundleStates(nodes.getNodeIds())).build();
    return null;
  }

  /**
   * Spring bridge method to access to the module manager bean.
   * @return an instance of the module manager service
   */
  private ModuleManager getModuleManager() {
      if (moduleManager == null) {
          moduleManager = ModuleManagerApplicationContext.getBean("ModuleManager", ModuleManager.class);
      }
      return moduleManager;
  }

}
