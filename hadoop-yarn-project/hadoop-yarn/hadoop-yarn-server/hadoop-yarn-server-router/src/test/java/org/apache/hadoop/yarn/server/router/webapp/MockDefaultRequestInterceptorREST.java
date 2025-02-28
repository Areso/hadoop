/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.yarn.server.router.webapp;

import java.io.IOException;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.lang3.EnumUtils;
import org.apache.hadoop.security.authorize.AuthorizationException;
import org.apache.hadoop.util.Sets;
import org.apache.hadoop.util.StringUtils;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.Resource;
import org.apache.hadoop.yarn.api.records.ContainerId;
import org.apache.hadoop.yarn.api.records.ApplicationAttemptId;
import org.apache.hadoop.yarn.api.records.NodeId;
import org.apache.hadoop.yarn.api.records.Priority;
import org.apache.hadoop.yarn.api.records.ContainerState;
import org.apache.hadoop.yarn.api.records.ContainerReport;
import org.apache.hadoop.yarn.api.records.NodeLabel;
import org.apache.hadoop.yarn.api.records.SignalContainerCommand;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.api.records.YarnApplicationState;
import org.apache.hadoop.yarn.api.records.FinalApplicationStatus;
import org.apache.hadoop.yarn.api.records.ApplicationAttemptReport;
import org.apache.hadoop.yarn.api.records.YarnApplicationAttemptState;
import org.apache.hadoop.yarn.exceptions.ApplicationNotFoundException;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.apache.hadoop.yarn.server.federation.store.records.SubClusterId;
import org.apache.hadoop.yarn.server.resourcemanager.webapp.NodeIDsInfo;
import org.apache.hadoop.yarn.server.resourcemanager.webapp.dao.NodeLabelsInfo;
import org.apache.hadoop.yarn.server.resourcemanager.webapp.dao.LabelsToNodesInfo;
import org.apache.hadoop.yarn.server.resourcemanager.webapp.dao.AppInfo;
import org.apache.hadoop.yarn.server.resourcemanager.webapp.dao.AppState;
import org.apache.hadoop.yarn.server.resourcemanager.webapp.dao.ApplicationSubmissionContextInfo;
import org.apache.hadoop.yarn.server.resourcemanager.webapp.dao.AppsInfo;
import org.apache.hadoop.yarn.server.resourcemanager.webapp.dao.ClusterMetricsInfo;
import org.apache.hadoop.yarn.server.resourcemanager.webapp.dao.NewApplication;
import org.apache.hadoop.yarn.server.resourcemanager.webapp.dao.NodeInfo;
import org.apache.hadoop.yarn.server.resourcemanager.webapp.dao.NodesInfo;
import org.apache.hadoop.yarn.server.resourcemanager.webapp.dao.ResourceInfo;
import org.apache.hadoop.yarn.server.resourcemanager.webapp.dao.ResourceOptionInfo;
import org.apache.hadoop.yarn.server.resourcemanager.webapp.dao.NodeToLabelsInfo;
import org.apache.hadoop.yarn.server.resourcemanager.webapp.dao.NodeLabelInfo;
import org.apache.hadoop.yarn.server.resourcemanager.webapp.dao.AppAttemptsInfo;
import org.apache.hadoop.yarn.server.webapp.dao.AppAttemptInfo;
import org.apache.hadoop.yarn.server.webapp.dao.ContainerInfo;
import org.apache.hadoop.yarn.server.webapp.dao.ContainersInfo;
import org.apache.hadoop.yarn.webapp.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class mocks the RESTRequestInterceptor.
 */
public class MockDefaultRequestInterceptorREST
    extends DefaultRequestInterceptorREST {

  private static final Logger LOG =
      LoggerFactory.getLogger(MockDefaultRequestInterceptorREST.class);
  final private AtomicInteger applicationCounter = new AtomicInteger(0);
  // True if the Mock RM is running, false otherwise.
  // This property allows us to write tests for specific scenario as YARN RM
  // down e.g. network issue, failover.
  private boolean isRunning = true;
  private HashSet<ApplicationId> applicationMap = new HashSet<>();
  public static final String APP_STATE_RUNNING = "RUNNING";

  private void validateRunning() throws ConnectException {
    if (!isRunning) {
      throw new ConnectException("RM is stopped");
    }
  }

  @Override
  public Response createNewApplication(HttpServletRequest hsr)
      throws AuthorizationException, IOException, InterruptedException {
    validateRunning();

    ApplicationId applicationId =
        ApplicationId.newInstance(Integer.valueOf(getSubClusterId().getId()),
            applicationCounter.incrementAndGet());
    NewApplication appId =
        new NewApplication(applicationId.toString(), new ResourceInfo());
    return Response.status(Status.OK).entity(appId).build();
  }

  @Override
  public Response submitApplication(ApplicationSubmissionContextInfo newApp,
      HttpServletRequest hsr)
      throws AuthorizationException, IOException, InterruptedException {
    validateRunning();

    ApplicationId appId = ApplicationId.fromString(newApp.getApplicationId());
    LOG.info("Application submitted: " + appId);
    applicationMap.add(appId);
    return Response.status(Status.ACCEPTED).header(HttpHeaders.LOCATION, "")
        .entity(getSubClusterId()).build();
  }

  @Override
  public AppInfo getApp(HttpServletRequest hsr, String appId,
      Set<String> unselectedFields) {
    if (!isRunning) {
      throw new RuntimeException("RM is stopped");
    }

    ApplicationId applicationId = ApplicationId.fromString(appId);
    if (!applicationMap.contains(applicationId)) {
      throw new NotFoundException("app with id: " + appId + " not found");
    }

    return new AppInfo();
  }

  @Override
  public AppsInfo getApps(HttpServletRequest hsr, String stateQuery,
      Set<String> statesQuery, String finalStatusQuery, String userQuery,
      String queueQuery, String count, String startedBegin, String startedEnd,
      String finishBegin, String finishEnd, Set<String> applicationTypes,
      Set<String> applicationTags, String name, Set<String> unselectedFields) {
    if (!isRunning) {
      throw new RuntimeException("RM is stopped");
    }
    AppsInfo appsInfo = new AppsInfo();
    AppInfo appInfo = new AppInfo();

    appInfo.setAppId(
        ApplicationId.newInstance(Integer.valueOf(getSubClusterId().getId()),
            applicationCounter.incrementAndGet()).toString());
    appInfo.setAMHostHttpAddress("http://i_am_the_AM:1234");

    appsInfo.add(appInfo);
    return appsInfo;
  }

  @Override
  public Response updateAppState(AppState targetState, HttpServletRequest hsr,
      String appId) throws AuthorizationException, YarnException,
      InterruptedException, IOException {
    validateRunning();

    ApplicationId applicationId = ApplicationId.fromString(appId);
    if (!applicationMap.remove(applicationId)) {
      throw new ApplicationNotFoundException(
          "Trying to kill an absent application: " + appId);
    }

    if (targetState == null) {
      return Response.status(Status.BAD_REQUEST).build();
    }

    LOG.info("Force killing application: " + appId);
    AppState ret = new AppState();
    ret.setState(targetState.toString());
    return Response.status(Status.OK).entity(ret).build();
  }

  @Override
  public NodeInfo getNode(String nodeId) {
    if (!isRunning) {
      throw new RuntimeException("RM is stopped");
    }
    NodeInfo node = new NodeInfo();
    node.setId(nodeId);
    node.setLastHealthUpdate(Integer.valueOf(getSubClusterId().getId()));
    return node;
  }

  @Override
  public NodesInfo getNodes(String states) {
    if (!isRunning) {
      throw new RuntimeException("RM is stopped");
    }
    NodeInfo node = new NodeInfo();
    node.setId("Node " + Integer.valueOf(getSubClusterId().getId()));
    node.setLastHealthUpdate(Integer.valueOf(getSubClusterId().getId()));
    NodesInfo nodes = new NodesInfo();
    nodes.add(node);
    return nodes;
  }

  @Override
  public ResourceInfo updateNodeResource(HttpServletRequest hsr,
      String nodeId, ResourceOptionInfo resourceOption) {
    if (!isRunning) {
      throw new RuntimeException("RM is stopped");
    }
    Resource resource = resourceOption.getResourceOption().getResource();
    return new ResourceInfo(resource);
  }

  @Override
  public ClusterMetricsInfo getClusterMetricsInfo() {
    if (!isRunning) {
      throw new RuntimeException("RM is stopped");
    }
    ClusterMetricsInfo metrics = new ClusterMetricsInfo();
    metrics.setAppsSubmitted(Integer.valueOf(getSubClusterId().getId()));
    metrics.setAppsCompleted(Integer.valueOf(getSubClusterId().getId()));
    metrics.setAppsPending(Integer.valueOf(getSubClusterId().getId()));
    metrics.setAppsRunning(Integer.valueOf(getSubClusterId().getId()));
    metrics.setAppsFailed(Integer.valueOf(getSubClusterId().getId()));
    metrics.setAppsKilled(Integer.valueOf(getSubClusterId().getId()));

    return metrics;
  }

  @Override
  public AppState getAppState(HttpServletRequest hsr, String appId)
      throws AuthorizationException {
    if (!isRunning) {
      throw new RuntimeException("RM is stopped");
    }

    ApplicationId applicationId = ApplicationId.fromString(appId);
    if (!applicationMap.contains(applicationId)) {
      throw new NotFoundException("app with id: " + appId + " not found");
    }

    return new AppState(APP_STATE_RUNNING);
  }

  public void setSubClusterId(int subClusterId) {
    setSubClusterId(SubClusterId.newInstance(Integer.toString(subClusterId)));
  }

  public boolean isRunning() {
    return isRunning;
  }

  public void setRunning(boolean runningMode) {
    this.isRunning = runningMode;
  }

  @Override
  public ContainersInfo getContainers(HttpServletRequest req, HttpServletResponse res,
      String appId, String appAttemptId) {
    if (!isRunning) {
      throw new RuntimeException("RM is stopped");
    }

    // We avoid to check if the Application exists in the system because we need
    // to validate that each subCluster returns 1 container.
    ContainersInfo containers = new ContainersInfo();

    int subClusterId = Integer.valueOf(getSubClusterId().getId());

    ContainerId containerId = ContainerId.newContainerId(
        ApplicationAttemptId.fromString(appAttemptId), subClusterId);
    Resource allocatedResource =
        Resource.newInstance(subClusterId, subClusterId);

    NodeId assignedNode = NodeId.newInstance("Node", subClusterId);
    Priority priority = Priority.newInstance(subClusterId);
    long creationTime = subClusterId;
    long finishTime = subClusterId;
    String diagnosticInfo = "Diagnostic " + subClusterId;
    String logUrl = "Log " + subClusterId;
    int containerExitStatus = subClusterId;
    ContainerState containerState = ContainerState.COMPLETE;
    String nodeHttpAddress = "HttpAddress " + subClusterId;

    ContainerReport containerReport = ContainerReport.newInstance(
        containerId, allocatedResource, assignedNode, priority,
        creationTime, finishTime, diagnosticInfo, logUrl,
        containerExitStatus, containerState, nodeHttpAddress);

    ContainerInfo container = new ContainerInfo(containerReport);
    containers.add(container);

    return containers;
  }

  @Override
  public NodeToLabelsInfo getNodeToLabels(HttpServletRequest hsr) throws IOException {
    if (!isRunning) {
      throw new RuntimeException("RM is stopped");
    }
    NodeLabelsInfo cpuNode = new NodeLabelsInfo(Collections.singleton("CPU"));
    NodeLabelsInfo gpuNode = new NodeLabelsInfo(Collections.singleton("GPU"));

    HashMap<String, NodeLabelsInfo> nodeLabels = new HashMap<>();
    nodeLabels.put("node1", cpuNode);
    nodeLabels.put("node2", gpuNode);
    return new NodeToLabelsInfo(nodeLabels);
  }

  @Override
  public LabelsToNodesInfo getLabelsToNodes(Set<String> labels) throws IOException {
    if (!isRunning) {
      throw new RuntimeException("RM is stopped");
    }

    Map<NodeLabelInfo, NodeIDsInfo> labelsToNodes = new HashMap<>();

    NodeLabel labelX = NodeLabel.newInstance("x", false);
    NodeLabelInfo nodeLabelInfoX = new NodeLabelInfo(labelX);
    ArrayList<String> hostsX = new ArrayList<>(Arrays.asList("host1A", "host1B"));
    Resource resourceX = Resource.newInstance(20*1024, 10);
    NodeIDsInfo nodeIDsInfoX = new NodeIDsInfo(hostsX, resourceX);
    labelsToNodes.put(nodeLabelInfoX, nodeIDsInfoX);

    NodeLabel labelY = NodeLabel.newInstance("y", false);
    NodeLabelInfo nodeLabelInfoY = new NodeLabelInfo(labelY);
    ArrayList<String> hostsY = new ArrayList<>(Arrays.asList("host2A", "host2B"));
    Resource resourceY = Resource.newInstance(40*1024, 20);
    NodeIDsInfo nodeIDsInfoY = new NodeIDsInfo(hostsY, resourceY);
    labelsToNodes.put(nodeLabelInfoY, nodeIDsInfoY);

    NodeLabel labelZ = NodeLabel.newInstance("z", false);
    NodeLabelInfo nodeLabelInfoZ = new NodeLabelInfo(labelZ);
    ArrayList<String> hostsZ = new ArrayList<>(Arrays.asList("host3A", "host3B"));
    Resource resourceZ = Resource.newInstance(80*1024, 40);
    NodeIDsInfo nodeIDsInfoZ = new NodeIDsInfo(hostsZ, resourceZ);
    labelsToNodes.put(nodeLabelInfoZ, nodeIDsInfoZ);

    return new LabelsToNodesInfo(labelsToNodes);
  }

  @Override
  public NodeLabelsInfo getClusterNodeLabels(HttpServletRequest hsr) throws IOException {
    if (!isRunning) {
      throw new RuntimeException("RM is stopped");
    }
    NodeLabel labelCpu = NodeLabel.newInstance("cpu", false);
    NodeLabel labelGpu = NodeLabel.newInstance("gpu", false);
    return new NodeLabelsInfo(Sets.newHashSet(labelCpu, labelGpu));
  }

  @Override
  public NodeLabelsInfo getLabelsOnNode(HttpServletRequest hsr, String nodeId) throws IOException {
    if (!isRunning) {
      throw new RuntimeException("RM is stopped");
    }

    if (StringUtils.equalsIgnoreCase(nodeId, "node1")) {
      NodeLabel labelCpu = NodeLabel.newInstance("x", false);
      NodeLabel labelGpu = NodeLabel.newInstance("y", false);
      return new NodeLabelsInfo(Sets.newHashSet(labelCpu, labelGpu));
    } else {
      return null;
    }
  }

  @Override
  public ContainerInfo getContainer(HttpServletRequest req, HttpServletResponse res,
      String appId, String appAttemptId, String containerId) {
    if (!isRunning) {
      throw new RuntimeException("RM is stopped");
    }

    ContainerId newContainerId = ContainerId.newContainerId(
        ApplicationAttemptId.fromString(appAttemptId), Integer.valueOf(containerId));

    Resource allocatedResource = Resource.newInstance(1024, 2);

    int subClusterId = Integer.valueOf(getSubClusterId().getId());
    NodeId assignedNode = NodeId.newInstance("Node", subClusterId);
    Priority priority = Priority.newInstance(subClusterId);
    long creationTime = subClusterId;
    long finishTime = subClusterId;
    String diagnosticInfo = "Diagnostic " + subClusterId;
    String logUrl = "Log " + subClusterId;
    int containerExitStatus = subClusterId;
    ContainerState containerState = ContainerState.COMPLETE;
    String nodeHttpAddress = "HttpAddress " + subClusterId;

    ContainerReport containerReport = ContainerReport.newInstance(
        newContainerId, allocatedResource, assignedNode, priority,
        creationTime, finishTime, diagnosticInfo, logUrl,
        containerExitStatus, containerState, nodeHttpAddress);

    return new ContainerInfo(containerReport);
  }

  @Override
  public Response signalToContainer(String containerId, String command,
      HttpServletRequest req) throws AuthorizationException {
    if (!isRunning) {
      throw new RuntimeException("RM is stopped");
    }

    if (!EnumUtils.isValidEnum(SignalContainerCommand.class, command.toUpperCase())) {
      String errMsg = "Invalid command: " + command.toUpperCase() + ", valid commands are: "
          + Arrays.asList(SignalContainerCommand.values());
      return Response.status(Status.BAD_REQUEST).entity(errMsg).build();
    }

    return Response.status(Status.OK).build();
  }

  @Override
  public AppAttemptInfo getAppAttempt(HttpServletRequest req, HttpServletResponse res,
      String appId, String appAttemptId) {
    if (!isRunning) {
      throw new RuntimeException("RM is stopped");
    }

    ApplicationId applicationId = ApplicationId.fromString(appId);
    if (!applicationMap.contains(applicationId)) {
      throw new NotFoundException("app with id: " + appId + " not found");
    }

    ApplicationReport newApplicationReport = ApplicationReport.newInstance(
        applicationId, ApplicationAttemptId.newInstance(applicationId, Integer.parseInt(appAttemptId)),
        "user", "queue", "appname", "host", 124, null,
        YarnApplicationState.RUNNING, "diagnostics", "url", 1, 2, 3, 4,
        FinalApplicationStatus.SUCCEEDED, null, "N/A", 0.53789f, "YARN", null);

    ApplicationAttemptReport attempt = ApplicationAttemptReport.newInstance(
        ApplicationAttemptId.newInstance(applicationId, Integer.parseInt(appAttemptId)),
        "host", 124, "url", "oUrl", "diagnostics",
        YarnApplicationAttemptState.FINISHED, ContainerId.newContainerId(
        newApplicationReport.getCurrentApplicationAttemptId(), 1));

    return new AppAttemptInfo(attempt);
  }

  @Override
  public AppAttemptsInfo getAppAttempts(HttpServletRequest hsr, String appId) {
    if (!isRunning) {
      throw new RuntimeException("RM is stopped");
    }

    ApplicationId applicationId = ApplicationId.fromString(appId);
    if (!applicationMap.contains(applicationId)) {
      throw new NotFoundException("app with id: " + appId + " not found");
    }

    AppAttemptsInfo infos = new AppAttemptsInfo();
    infos.add(TestRouterWebServiceUtil.generateAppAttemptInfo(0));
    infos.add(TestRouterWebServiceUtil.generateAppAttemptInfo(1));
    return infos;
  }
}