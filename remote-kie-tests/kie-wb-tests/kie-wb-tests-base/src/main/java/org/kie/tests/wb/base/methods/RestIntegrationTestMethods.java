/*
 * JBoss, Home of Professional Open Source
 * 
 * Copyright 2012, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kie.tests.wb.base.methods;

import static org.junit.Assert.*;
import static org.kie.tests.wb.base.methods.TestConstants.*;

import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.MediaType;

import org.apache.commons.net.util.Base64;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.DefaultHttpClient;
import org.drools.core.command.runtime.process.StartProcessCommand;
import org.jboss.resteasy.client.ClientExecutor;
import org.jboss.resteasy.client.ClientRequest;
import org.jboss.resteasy.client.ClientRequestFactory;
import org.jboss.resteasy.client.ClientResponse;
import org.jboss.resteasy.client.core.executors.ApacheHttpClient4Executor;
import org.jboss.resteasy.spi.ReaderException;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.jboss.resteasy.util.HttpHeaderNames;
import org.jbpm.kie.services.impl.KModuleDeploymentUnit;
import org.jbpm.process.audit.ProcessInstanceLog;
import org.jbpm.process.audit.VariableInstanceLog;
import org.jbpm.process.audit.event.AuditEvent;
import org.jbpm.services.task.commands.CompleteTaskCommand;
import org.jbpm.services.task.commands.GetTaskCommand;
import org.jbpm.services.task.commands.GetTasksByProcessInstanceIdCommand;
import org.jbpm.services.task.commands.StartTaskCommand;
import org.jbpm.services.task.impl.model.xml.JaxbTask;
import org.kie.api.command.Command;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.manager.RuntimeEngine;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.task.TaskService;
import org.kie.api.task.model.Status;
import org.kie.api.task.model.Task;
import org.kie.api.task.model.TaskData;
import org.kie.api.task.model.TaskSummary;
import org.kie.internal.deployment.DeploymentUnit.RuntimeStrategy;
import org.kie.services.client.api.RemoteRestRuntimeFactory;
import org.kie.services.client.api.RestRequestHelper;
import org.kie.services.client.api.command.RemoteRuntimeEngine;
import org.kie.services.client.serialization.JaxbSerializationProvider;
import org.kie.services.client.serialization.JsonSerializationProvider;
import org.kie.services.client.serialization.jaxb.impl.JaxbCommandResponse;
import org.kie.services.client.serialization.jaxb.impl.JaxbCommandsRequest;
import org.kie.services.client.serialization.jaxb.impl.JaxbCommandsResponse;
import org.kie.services.client.serialization.jaxb.impl.JaxbExceptionResponse;
import org.kie.services.client.serialization.jaxb.impl.JaxbLongListResponse;
import org.kie.services.client.serialization.jaxb.impl.audit.AbstractJaxbHistoryObject;
import org.kie.services.client.serialization.jaxb.impl.audit.JaxbHistoryLogList;
import org.kie.services.client.serialization.jaxb.impl.audit.JaxbVariableInstanceLog;
import org.kie.services.client.serialization.jaxb.impl.deploy.JaxbDeploymentJobResult;
import org.kie.services.client.serialization.jaxb.impl.deploy.JaxbDeploymentUnit;
import org.kie.services.client.serialization.jaxb.impl.deploy.JaxbDeploymentUnit.JaxbDeploymentStatus;
import org.kie.services.client.serialization.jaxb.impl.process.JaxbProcessInstanceResponse;
import org.kie.services.client.serialization.jaxb.impl.task.JaxbTaskSummaryListResponse;
import org.kie.services.client.serialization.jaxb.rest.JaxbGenericResponse;
import org.kie.tests.wb.base.services.data.JaxbProcessInstanceSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestIntegrationTestMethods extends AbstractIntegrationTestMethods {

    private static Logger logger = LoggerFactory.getLogger(RestIntegrationTestMethods.class);

    private static final String taskUserId = "salaboy";

    private final String deploymentId;
    private String mediaType;

    private int timeout = 10;
    
    public RestIntegrationTestMethods(String deploymentId, String mediaType, int timeout) {
       this(deploymentId, mediaType);
       this.timeout = timeout;
    }
    
    public RestIntegrationTestMethods(String deploymentId, String mediaType) {
        this.deploymentId = deploymentId;
        this.mediaType = mediaType;
    }

    public RestIntegrationTestMethods(String deploymentId) {
        this.deploymentId = deploymentId;
        this.mediaType = MediaType.APPLICATION_XML;
    }

    private JaxbSerializationProvider jaxbSerializationProvider = new JaxbSerializationProvider();
    private JsonSerializationProvider jsonSerializationProvider = new JsonSerializationProvider();

    private static SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");

    /**
     * Helper methods
     */

    private ClientResponse<?> checkResponse(ClientResponse<?> responseObj) throws Exception {
        return checkResponse(responseObj, 200);
    }

    private ClientResponse<?> checkResponse(ClientResponse<?> responseObj, int status) throws Exception {
        responseObj.resetStream();
        int reqStatus = responseObj.getStatus();
        if (reqStatus != status) {
            logger.warn("Response with exception:\n" + responseObj.getEntity(String.class));
            assertEquals("Status OK", status, reqStatus);
        }
        return responseObj;
    }

    public ClientResponse<?> get(ClientRequest restRequest) throws Exception {
        logger.debug(">> [GET]  " + restRequest.getUri());
        return checkResponse(restRequest.get());
    }

    public ClientResponse<?> post(ClientRequest restRequest) throws Exception {
        logger.debug(">> [POST/" + restRequest.getHeaders().getFirst(HttpHeaderNames.ACCEPT) + "] " + restRequest.getUri());
        return checkResponse(restRequest.post());
    }

    private ClientResponse<?> checkResponsePostTime(ClientRequest restRequest, int status) throws Exception {
        long before, after;
        logger.debug("BEFORE: " + sdf.format((before = System.currentTimeMillis())));
        ClientResponse<?> responseObj = checkResponse(restRequest.post(), 202);
        logger.debug("AFTER: " + sdf.format((after = System.currentTimeMillis())));
        assertTrue("Call took longer than " + restCallDurationLimit / 1000 + " seconds", (after - before) < restCallDurationLimit);
        return responseObj;
    }

    private void addToRequestBody(ClientRequest restRequest, Object obj) throws Exception {
        if (mediaType.equals(MediaType.APPLICATION_XML)) {
            String body = jaxbSerializationProvider.serialize(obj);
            restRequest.body(mediaType, body);
        } else if (mediaType.equals(MediaType.APPLICATION_JSON)) {
            String body = jsonSerializationProvider.serialize(obj);
            restRequest.body(mediaType, body);
        }
    }

    /**
     * Test methods
     */

    public void urlsStartHumanTaskProcess(URL deploymentUrl, String user, String password) throws Exception {
        RestRequestHelper requestHelper = RestRequestHelper.newInstance(deploymentUrl, user, password, timeout);

        // Start process
        ClientRequest restRequest = requestHelper.createRequest("runtime/" + deploymentId + "/process/org.jbpm.humantask/start");
        ClientResponse<?> responseObj = post(restRequest);
        JaxbProcessInstanceResponse processInstance = (JaxbProcessInstanceResponse) responseObj
                .getEntity(JaxbProcessInstanceResponse.class);
        long procInstId = processInstance.getId();

        // query tasks for associated task Id
        restRequest = requestHelper.createRequest("task/query?status=Reserved&processInstanceId=" + procInstId);
        responseObj = get(restRequest);

        JaxbTaskSummaryListResponse taskSumlistResponse = (JaxbTaskSummaryListResponse) responseObj
                .getEntity(JaxbTaskSummaryListResponse.class);
        TaskSummary taskSum = findTaskSummary(procInstId, taskSumlistResponse.getResult());
        long taskId = taskSum.getId();

        // get task info
        restRequest = requestHelper.createRequest("task/" + taskId);
        responseObj = get(restRequest);
        JaxbTask task = (JaxbTask) responseObj.getEntity(JaxbTask.class);
        assertEquals("Incorrect task id", taskId, task.getId().longValue());

        // start task
        restRequest = requestHelper.createRequest("task/" + taskId + "/start");
        responseObj = post(restRequest);
        JaxbGenericResponse resp = (JaxbGenericResponse) responseObj.getEntity(JaxbGenericResponse.class);
        assertNotNull("Response from task start is null.", resp);

        // get task info
        restRequest = requestHelper.createRequest("task/" + taskId);
        responseObj = get(restRequest);
    }

    public void commandsStartProcess(URL deploymentUrl, String user, String password) throws Exception {
        RestRequestHelper helper = RestRequestHelper.newInstance(deploymentUrl, user, password, timeout);
        helper.setMediaType(MediaType.APPLICATION_XML_TYPE);

        String originalType = this.mediaType;
        this.mediaType = MediaType.APPLICATION_XML;

        // Start process
        String executeOp = "runtime/" + deploymentId + "/execute";
        ClientRequest restRequest = helper.createRequest(executeOp);
        JaxbCommandsRequest commandMessage = new JaxbCommandsRequest(deploymentId, new StartProcessCommand(HUMAN_TASK_PROCESS_ID));
        addToRequestBody(restRequest, commandMessage);

        logger.debug(">> [startProcess] " + restRequest.getUri());
        ClientResponse<?> responseObj = post(restRequest);

        JaxbCommandsResponse cmdsResp = (JaxbCommandsResponse) responseObj.getEntity(JaxbCommandsResponse.class);
        assertFalse("Exception received!", cmdsResp.getResponses().get(0) instanceof JaxbExceptionResponse);
        long procInstId = ((ProcessInstance) cmdsResp.getResponses().get(0)).getId();

        // query tasks
        restRequest = helper.createRequest(executeOp);
        commandMessage = new JaxbCommandsRequest(deploymentId, new GetTasksByProcessInstanceIdCommand(procInstId));
        addToRequestBody(restRequest, commandMessage);

        logger.debug(">> [getTasksByProcessInstanceId] " + restRequest.getUri());
        responseObj = post(restRequest);
        JaxbCommandsResponse cmdResponse = (JaxbCommandsResponse) responseObj.getEntity(JaxbCommandsResponse.class);
        List<?> list = (List<?>) cmdResponse.getResponses().get(0).getResult();
        long taskId = (Long) list.get(0);

        // start task

        logger.debug(">> [startTask] " + restRequest.getUri());
        restRequest = helper.createRequest(executeOp);
        commandMessage = new JaxbCommandsRequest(new StartTaskCommand(taskId, taskUserId));
        addToRequestBody(restRequest, commandMessage);

        // Get response
        responseObj = post(restRequest);
        responseObj.releaseConnection();

        restRequest = helper.createRequest("task/execute");
        commandMessage = new JaxbCommandsRequest(new CompleteTaskCommand(taskId, taskUserId, null));
        addToRequestBody(restRequest, commandMessage);

        // Get response
        logger.debug(">> [completeTask] " + restRequest.getUri());
        post(restRequest);

        // TODO: check that above has completed?
        this.mediaType = originalType;
    }

    public void remoteApiHumanTaskProcess(URL deploymentUrl, String user, String password) throws Exception {
        // create REST request
        RemoteRestRuntimeFactory restSessionFactory = new RemoteRestRuntimeFactory(deploymentId, deploymentUrl, user, password);
        RuntimeEngine engine = restSessionFactory.newRuntimeEngine();
        KieSession ksession = engine.getKieSession();
        ProcessInstance processInstance = ksession.startProcess(HUMAN_TASK_PROCESS_ID);

        logger.debug("Started process instance: " + processInstance + " "
                + (processInstance == null ? "" : processInstance.getId()));

        TaskService taskService = engine.getTaskService();
        List<TaskSummary> tasks = taskService.getTasksAssignedAsPotentialOwner(taskUserId, "en-UK");
        long taskId = findTaskId(processInstance.getId(), tasks);

        logger.debug("Found task " + taskId);
        Task task = taskService.getTaskById(taskId);
        logger.debug("Got task " + taskId + ": " + task);
        taskService.start(taskId, taskUserId);
        taskService.complete(taskId, taskUserId, null);

        logger.debug("Now expecting failure");
        try {
            taskService.complete(taskId, taskUserId, null);
            fail("Should not be able to complete task " + taskId + " a second time.");
        } catch (Throwable t) {
            logger.info("The above exception was an expected part of the test.");
            // do nothing
        }

        List<Status> statuses = new ArrayList<Status>();
        statuses.add(Status.Reserved);
        List<TaskSummary> taskIds = taskService.getTasksByStatusByProcessInstanceId(processInstance.getId(), statuses, "en-UK");
        assertEquals("Expected 2 tasks.", 2, taskIds.size());
    }

    public void commandsTaskCommands(URL deploymentUrl, String user, String password) throws Exception {

        RuntimeEngine runtimeEngine = new RemoteRestRuntimeFactory(deploymentId, deploymentUrl, user, password).newRuntimeEngine();
        KieSession ksession = runtimeEngine.getKieSession();
        ProcessInstance processInstance = ksession.startProcess(HUMAN_TASK_PROCESS_ID);

        long processInstanceId = processInstance.getId();
        JaxbCommandResponse<?> response = executeCommand(deploymentUrl, user, password, deploymentId,
                new GetTasksByProcessInstanceIdCommand(processInstanceId));

        long taskId = ((JaxbLongListResponse) response).getResult().get(0);
        assertTrue("task id is less than 0", taskId > 0);

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("userId", taskUserId);
    }

    private JaxbCommandResponse<?> executeCommand(URL appUrl, String user, String password, String deploymentId, Command<?> command)
            throws Exception {
        RestRequestHelper requestHelper = RestRequestHelper.newInstance(appUrl, user, password, timeout);

        String originalMediaType = this.mediaType;
        this.mediaType = MediaType.APPLICATION_XML;

        List<Command<?>> commands = new ArrayList<Command<?>>();
        commands.add(command);

        ClientRequest restRequest = requestHelper.createRequest("runtime/" + deploymentId + "/execute");

        JaxbCommandsRequest commandMessage = new JaxbCommandsRequest(commands);
        assertNotNull("Commands are null!", commandMessage.getCommands());
        assertTrue("Commands are empty!", commandMessage.getCommands().size() > 0);

        addToRequestBody(restRequest, commandMessage);

        logger.debug(">> [" + command.getClass().getSimpleName() + "] " + restRequest.getUri());
        ClientResponse<JaxbCommandsResponse> responseObj = restRequest.post(JaxbCommandsResponse.class);
        checkResponse(responseObj);

        JaxbCommandsResponse cmdsResp = responseObj.getEntity();

        this.mediaType = originalMediaType;
        return cmdsResp.getResponses().get(0);
    }

    public void urlsHistoryLogs(URL deploymentUrl, String user, String password) throws Exception {
        RestRequestHelper helper = RestRequestHelper.newInstance(deploymentUrl, user, password, timeout);

        // Start process
        ClientRequest restRequest = helper.createRequest("runtime/" + deploymentId + "/process/" + SCRIPT_TASK_VAR_PROCESS_ID + "/start?map_x=initVal");
        logger.debug(">> " + restRequest.getUri());
        ClientResponse<?> responseObj = post(restRequest);
        JaxbProcessInstanceResponse processInstance = (JaxbProcessInstanceResponse) responseObj
                .getEntity(JaxbProcessInstanceResponse.class);
        long procInstId = processInstance.getId();

        // instances/
        {
            String histOp = "/history/instances";
            restRequest = helper.createRequest("runtime/" + deploymentId + histOp);
            logger.debug(">> [history] " + restRequest.getUri());
            responseObj = get(restRequest);
            JaxbHistoryLogList runtimeResult = (JaxbHistoryLogList) responseObj.getEntity(JaxbHistoryLogList.class);
            List<AuditEvent> runtimeLogList = runtimeResult.getResult();

            restRequest = helper.createRequest(histOp);
            logger.debug(">> [runtime] " + restRequest.getUri());
            responseObj = get(restRequest);
            JaxbHistoryLogList historyResult = (JaxbHistoryLogList) responseObj.getEntity(JaxbHistoryLogList.class);
            List<AuditEvent> historyLogList = runtimeResult.getResult();

            assertEquals("command name", historyResult.getCommandName(), runtimeResult.getCommandName());
            assertEquals("list size", historyLogList.size(), runtimeLogList.size());

            for (AuditEvent event : historyLogList) {
                assertTrue("ProcessInstanceLog", event instanceof ProcessInstanceLog);
                ProcessInstanceLog procLog = (ProcessInstanceLog) event;
                Object[][] out = { { procLog.getDuration(), "duration" }, { procLog.getEnd(), "end date" },
                        { procLog.getExternalId(), "externalId" }, { procLog.getId(), "id" },
                        { procLog.getIdentity(), "identity" }, { procLog.getOutcome(), "outcome" },
                        { procLog.getParentProcessInstanceId(), "parent proc id" }, { procLog.getProcessId(), "process id" },
                        { procLog.getProcessInstanceId(), "process instance id" }, { procLog.getProcessName(), "process name" },
                        { procLog.getProcessVersion(), "process version" }, { procLog.getStart(), "start date" },
                        { procLog.getStatus(), "status" } };
                for (int i = 0; i < out.length; ++i) {
                    // System.out.println(out[i][1] + ": " + out[i][0]);
                }
            }
        }
        // instance/{procInstId}

        // instance/{procInstId}/child

        // instance/{procInstId}/node

        // instance/{procInstId}/variable

        // instance/{procInstId}/node/{nodeId}

        // instance/{procInstId}/variable/{variable}
        restRequest = helper.createRequest("runtime/" + deploymentId + "/history/instance/" + procInstId + "/variable/x");
        logger.debug(">> [runtime]" + restRequest.getUri());
        responseObj = get(restRequest);
        JaxbHistoryLogList historyLogList = (JaxbHistoryLogList) responseObj.getEntity(JaxbHistoryLogList.class);
        List<AbstractJaxbHistoryObject> historyVarLogList = historyLogList.getHistoryLogList();
        
        restRequest = helper.createRequest("runtime/" + deploymentId + "/history/instance/" + procInstId + "/variable/x");
        logger.debug(">> [runtime]" + restRequest.getUri());
        responseObj = get(restRequest);
        JaxbHistoryLogList runtimeLogList = (JaxbHistoryLogList) responseObj.getEntity(JaxbHistoryLogList.class);
        List<AbstractJaxbHistoryObject> runtimeVarLogList = runtimeLogList.getHistoryLogList();
        assertTrue("Incorrect number of variable logs: " + runtimeVarLogList.size(), 4 <= runtimeVarLogList.size());

        assertEquals( "runtime/history list size", historyVarLogList.size(), runtimeVarLogList.size());
        
        for(int i = 0; i < runtimeVarLogList.size(); ++i) {
            JaxbVariableInstanceLog varLog = (JaxbVariableInstanceLog) runtimeVarLogList.get(i);
            JaxbVariableInstanceLog historyVarLog = (JaxbVariableInstanceLog) historyVarLogList.get(i);
            assertEquals(historyVarLog.getValue(), varLog.getValue());
            
            assertEquals("Incorrect variable id", "x", varLog.getVariableId());
            assertEquals("Incorrect process id", SCRIPT_TASK_VAR_PROCESS_ID, varLog.getProcessId());
            assertEquals("Incorrect process instance id", procInstId, varLog.getProcessInstanceId().longValue());
        }

        // process/{procDefId}

        // variable/{varId}

        // variable/{varId}/{value}

        // runtime/{depId}/history/variable/{varId}/instances

        // runtime/{depId}/history/variable/{varId}/value/{val}/instances

    }

    public void urlsDataServiceCoupling(URL deploymentUrl, String user, String password) throws Exception {
        RestRequestHelper requestHelper = RestRequestHelper.newInstance(deploymentUrl, user, password, timeout);
        ClientRequest restRequest = requestHelper.createRequest("runtime/" + deploymentId + "/process/"
                + SCRIPT_TASK_VAR_PROCESS_ID + "/start?map_x=initVal");

        // Get and check response
        logger.debug(">> " + restRequest.getUri());
        ClientResponse<?> responseObj = post(restRequest);
        JaxbProcessInstanceResponse processInstance = (JaxbProcessInstanceResponse) responseObj
                .getEntity(JaxbProcessInstanceResponse.class);
        long procInstId = processInstance.getId();

        restRequest = requestHelper.createRequest("data/process/instance/" + procInstId);
        logger.debug(">> [data/process instance]" + restRequest.getUri());
        responseObj = get(restRequest);
        JaxbProcessInstanceSummary summary = (JaxbProcessInstanceSummary) responseObj.getEntity(JaxbProcessInstanceSummary.class);
        assertEquals("Incorrect initiator.", user, summary.getInitiator());
    }

    public void urlsJsonJaxbStartProcess(URL deploymentUrl, String user, String password) throws Exception {
        RestRequestHelper requestHelper = RestRequestHelper.newInstance(deploymentUrl, user, password);

        // XML
        String startProcessOper = "runtime/" + deploymentId + "/process/org.jbpm.humantask/start";
        ClientRequest restRequest = requestHelper.createRequest(startProcessOper);
        restRequest.accept(MediaType.APPLICATION_XML_TYPE);
        logger.debug(">> " + restRequest.getUri());
        ClientResponse<?> responseObj = post(restRequest);
        String result = (String) responseObj.getEntity(String.class);
        assertTrue("Doesn't start like a JAXB string!", result.startsWith("<"));

        // JSON
        restRequest = requestHelper.createRequest(startProcessOper);
        restRequest.accept(MediaType.APPLICATION_JSON_TYPE);
        logger.debug(">> " + restRequest.getUri());
        responseObj = post(restRequest);
        result = (String) responseObj.getEntity(String.class);
        assertTrue("Doesn't start like a JSON string!", result.startsWith("{"));
    }

    public void urlsHumanTaskWithFormVariableChange(URL deploymentUrl, String user, String password) throws Exception {
        RestRequestHelper requestHelper = RestRequestHelper.newInstance(deploymentUrl, user, password);
        requestHelper.setMediaType(MediaType.APPLICATION_XML_TYPE);

        // Start process
        ClientRequest restRequest = requestHelper.createRequest("runtime/" + deploymentId + "/process/" + HUMAN_TASK_VAR_PROCESS_ID
                + "/start?map_userName=John");
        ClientResponse<?> responseObj = post(restRequest);
        JaxbProcessInstanceResponse processInstance = (JaxbProcessInstanceResponse) responseObj
                .getEntity(JaxbProcessInstanceResponse.class);
        long procInstId = processInstance.getId();

        // query tasks for associated task Id
        restRequest = requestHelper.createRequest("task/query?processInstanceId=" + procInstId);
        responseObj = get(restRequest);

        JaxbTaskSummaryListResponse taskSumlistResponse = (JaxbTaskSummaryListResponse) responseObj
                .getEntity(JaxbTaskSummaryListResponse.class);
        TaskSummary taskSum = findTaskSummary(procInstId, taskSumlistResponse.getResult());
        long taskId = taskSum.getId();

        // start task
        restRequest = requestHelper.createRequest("task/" + taskId + "/start");
        responseObj = post(restRequest);
        JaxbGenericResponse resp = (JaxbGenericResponse) responseObj.getEntity(JaxbGenericResponse.class);
        assertNotNull("Response from task start operation is null.", resp);

        // complete task
        String georgeVal = "George";
        restRequest = requestHelper.createRequest("task/" + taskId + "/complete?map_outUserName=" + georgeVal);
        responseObj = post(restRequest);
        resp = (JaxbGenericResponse) responseObj.getEntity(JaxbGenericResponse.class);

        restRequest = requestHelper.createRequest("runtime/" + deploymentId + "/history/instance/" + procInstId
                + "/variable/userName");
        responseObj = get(restRequest);
        JaxbHistoryLogList histResp = (JaxbHistoryLogList) responseObj.getEntity(JaxbHistoryLogList.class);

        List<AbstractJaxbHistoryObject> histList = histResp.getHistoryLogList();
        boolean georgeFound = false;
        for (AbstractJaxbHistoryObject<VariableInstanceLog> absVarLog : histList) {
            VariableInstanceLog varLog = ((JaxbVariableInstanceLog) absVarLog).getResult();
            if ("userName".equals(varLog.getVariableId()) && georgeVal.equals(varLog.getValue())) {
                georgeFound = true;
            }
        }
        assertTrue("'userName' var with value '" + georgeVal + "' not found!", georgeFound);
    }

    public void urlsHttpURLConnectionAcceptHeaderIsFixed(URL deploymentUrl, String user, String password) throws Exception {
        URL url = new URL(deploymentUrl, deploymentUrl.getPath() + "rest/runtime/" + deploymentId + "/process/"
                + SCRIPT_TASK_PROCESS_ID + "/start");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        String authString = user + ":" + password;
        byte[] authEncBytes = Base64.encodeBase64(authString.getBytes());
        String authStringEnc = new String(authEncBytes);
        connection.setRequestProperty("Authorization", "Basic " + authStringEnc);
        connection.setRequestMethod("POST");

        logger.debug(">> [POST] " + url.toExternalForm());
        connection.connect();
        if (200 != connection.getResponseCode()) {
            logger.warn(connection.getContent().toString());
        }
        assertEquals(200, connection.getResponseCode());
    }

    public void remoteApiSerialization(URL deploymentUrl, String user, String password) throws Exception {
        // setup
        RemoteRestRuntimeFactory restSessionFactory = new RemoteRestRuntimeFactory(deploymentId, deploymentUrl, user, password);
        RuntimeEngine engine = restSessionFactory.newRuntimeEngine();
        KieSession ksession = engine.getKieSession();
       
        // start process
        ksession.startProcess(HUMAN_TASK_PROCESS_ID);
        Collection<ProcessInstance> processInstances = ksession.getProcessInstances();
        assertTrue("No process instances started.", processInstances != null && processInstances.size() > 0);
    }

    public void remoteApiExtraJaxbClasses(URL deploymentUrl, String user, String password) throws Exception {
        // Remote API setup
        RemoteRestRuntimeFactory restSessionFactory = new RemoteRestRuntimeFactory(deploymentId, deploymentUrl, user, password);
        RemoteRuntimeEngine engine = restSessionFactory.newRuntimeEngine();

        testExtraJaxbClassSerialization(engine);
    }

    private long restCallDurationLimit = 2000;
    private long sleep = 5000;

    public void urlsDeployModuleForOtherTests(URL deploymentUrl, String user, String password, MediaType mediaType)
            throws Exception {
        RestRequestHelper requestHelper = RestRequestHelper.newInstance(deploymentUrl, user, password, 10);

        String deploymentId = (new KModuleDeploymentUnit(GROUP_ID, ARTIFACT_ID, VERSION)).getIdentifier();
        RuntimeStrategy strategy = RuntimeStrategy.PER_PROCESS_INSTANCE;

        // Get (has it been deployed?)
        ClientRequest restRequest = requestHelper.createRequest("deployment/" + deploymentId + "/");

        logger.info("check undeployed");
        if (!checkUndeployed(restRequest.get())) {
            logger.info("undeploy");
            undeploy(deploymentId, deploymentUrl, requestHelper);
        }

        // Deploy
        logger.info("deploy");
        deploy(user, password, deploymentUrl, deploymentId, strategy, mediaType);
        waitForDeploymentJobToSucceed(deploymentId, true, deploymentUrl, requestHelper);
    }

    private void undeploy(String deploymentId, URL deploymentUrl, RestRequestHelper requestHelper) throws Exception {
        // Exists, so undeploy
        ClientRequest restRequest = requestHelper.createRequest("deployment/" + deploymentId + "/undeploy");

        ClientResponse<?> responseObj = checkResponsePostTime(restRequest, 202);

        JaxbDeploymentJobResult jaxbJobResult = responseObj.getEntity(JaxbDeploymentJobResult.class);
        assertEquals("Undeploy operation", jaxbJobResult.getOperation(), "UNDEPLOY");
        logger.info("UNDEPLOY : [" + jaxbJobResult.getDeploymentUnit().getStatus().toString() + "]"
                + jaxbJobResult.getExplanation());

        waitForDeploymentJobToSucceed(deploymentId, false, deploymentUrl, requestHelper);
    }

    private void waitForDeploymentJobToSucceed(String deploymentId, boolean deploy, URL deploymentUrl,
            RestRequestHelper requestHelper) throws Exception {
        boolean success = false;
        int tries = 0;
        while (!success && tries++ < MAX_TRIES) {
            ClientRequest restRequest = requestHelper.createRequest("deployment/" + deploymentId + "/");
            logger.debug(">> " + restRequest.getUri());
            ClientResponse<?> responseObj = restRequest.get();
            if (deploy) {
                success = checkDeployed(responseObj);
            } else {
                success = checkUndeployed(responseObj);
            }
            if (!success) {
                logger.info("Sleeping for " + sleep / 1000 + " seconds");
                Thread.sleep(sleep);
            }
        }
    }

    private boolean checkUndeployed(ClientResponse<?> responseObj) {
        int status = responseObj.getStatus();
        try {
            logger.info("status: " + status);
            if (status == 200) {
                JaxbDeploymentUnit jaxbDepUnit = responseObj.getEntity(JaxbDeploymentUnit.class);
                JaxbDeploymentStatus jaxbDepStatus = checkJaxbDeploymentUnitAndGetStatus(jaxbDepUnit, GROUP_ID, ARTIFACT_ID,
                        VERSION);
                logger.info("dep status: " + jaxbDepStatus);
                if (jaxbDepStatus == JaxbDeploymentStatus.UNDEPLOYED || jaxbDepStatus == JaxbDeploymentStatus.NONEXISTENT) {
                    return true;
                }
            } else if (status == 404) {
                return true;
            }
            return false;
        } finally {
            responseObj.releaseConnection();
        }
    }

    private boolean checkDeployed(ClientResponse<?> responseObj) {
        int status = responseObj.getStatus();
        try {
            if (status == 200) {
                JaxbDeploymentUnit jaxbDepUnit = responseObj.getEntity(JaxbDeploymentUnit.class);
                JaxbDeploymentStatus jaxbDepStatus = checkJaxbDeploymentUnitAndGetStatus(jaxbDepUnit, GROUP_ID, ARTIFACT_ID,
                        VERSION);
                if (jaxbDepStatus == JaxbDeploymentStatus.DEPLOYED) {
                    return true;
                }
            } else {
                return false;
            }
            return false;
        } finally {
            responseObj.releaseConnection();
        }
    }

    private JaxbDeploymentStatus checkJaxbDeploymentUnitAndGetStatus(JaxbDeploymentUnit jaxbDepUnit, String groupId,
            String artifactId, String version) {
        assertEquals("GroupId", GROUP_ID, jaxbDepUnit.getGroupId());
        assertEquals("ArtifactId", ARTIFACT_ID, jaxbDepUnit.getArtifactId());
        assertEquals("Version", VERSION, jaxbDepUnit.getVersion());
        return jaxbDepUnit.getStatus();
    }

    public void remoteApiRuleTaskProcess(URL deploymentUrl, String user, String password) {
        // Remote API setup
        RemoteRestRuntimeFactory restSessionFactory = new RemoteRestRuntimeFactory(deploymentId, deploymentUrl, user, password);
        RemoteRuntimeEngine runtimeEngine = restSessionFactory.newRuntimeEngine();

        // runTest
        runRuleTaskProcess(runtimeEngine.getKieSession(), runtimeEngine.getAuditLogService());
    }

    public void remoteApiGetTaskInstance(URL deploymentUrl, String user, String password) throws Exception {
        RestRequestHelper requestHelper = RestRequestHelper.newInstance(deploymentUrl, user, password);
        // Remote API setup
        RemoteRestRuntimeFactory restSessionFactory = new RemoteRestRuntimeFactory(deploymentId, deploymentUrl, user, password);
        RemoteRuntimeEngine engine = restSessionFactory.newRuntimeEngine();

        KieSession ksession = engine.getKieSession();
        ProcessInstance processInstance = null;
        try {
            processInstance = ksession.startProcess(HUMAN_TASK_PROCESS_ID);
        } catch (Exception e) {
            fail("Unable to start process: " + e.getMessage());
        }
        logger.debug("Started process instance: " + processInstance + " "
                + (processInstance == null ? "" : processInstance.getId()));

        TaskService taskService = engine.getTaskService();
        List<Long> tasks = taskService.getTasksByProcessInstanceId(processInstance.getId());
        assertEquals("Incorrect number of tasks for started process: ", 1, tasks.size());
        long taskId = tasks.get(0);

        // Get it via the command
        JaxbCommandResponse<?> response = executeCommand(deploymentUrl, user, password, deploymentId, new GetTaskCommand(taskId));
        Task task = (Task) response.getResult();
        checkReturnedTask(task, taskId);

        // Get it via the URL
        ClientRequest restRequest = requestHelper.createRequest("task/" + taskId);
        ClientResponse<?> responseObj = get(restRequest);
        JaxbTask jaxbTask = responseObj.getEntity(JaxbTask.class);
        checkReturnedTask((Task) jaxbTask, taskId);

        // Get it via the remote API
        task = engine.getTaskService().getTaskById(taskId);
        checkReturnedTask(task, taskId);
    }

    private void checkReturnedTask(Task task, long taskId) {
        assertNotNull("Could not retrietve task " + taskId, task);
        assertEquals("Incorrect task rertrieved", taskId, task.getId().longValue());
        TaskData taskData = task.getTaskData();
        assertNotNull(taskData);
    }

    private JaxbDeploymentJobResult deploy(String userId, String password, URL appUrl, String deploymentId,
            RuntimeStrategy strategy, MediaType mediaType) throws Exception {
        // This code has been refactored but is essentially the same as the org.jboss.qa.bpms.rest.wb.RestWorkbenchClient code

        // Create request
        String AUTH_HEADER = "Basic " + Base64.encodeBase64String(String.format("%s:%s", userId, password).getBytes()).trim();
        String url = appUrl.toExternalForm() + "rest/deployment/" + deploymentId + "/deploy";
        if (strategy.equals(RuntimeStrategy.SINGLETON)) {
            url += "?strategy=" + strategy.toString();
        }

        DefaultHttpClient httpClient = new DefaultHttpClient();
        httpClient.getCredentialsProvider().setCredentials(
                new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT, AuthScope.ANY_REALM),
                new UsernamePasswordCredentials(userId, password));
        ClientExecutor clientExecutor = new ApacheHttpClient4Executor(httpClient);
        ClientRequestFactory factory = new ClientRequestFactory(clientExecutor, ResteasyProviderFactory.getInstance());

        ClientRequest request = factory.createRequest(url).header("Authorization", AUTH_HEADER).accept(mediaType)
                .followRedirects(true);
        logger.debug(">> " + request.getUri());

        // ADDED CODE TO CHECK RESPONSE TIME
        long before, after;
        logger.debug("BEFORE POST: " + sdf.format((before = System.currentTimeMillis())));

        // POST request
        JaxbDeploymentJobResult result = null;
        ClientResponse<JaxbDeploymentJobResult> response = null;
        try {
            response = request.post(JaxbDeploymentJobResult.class);
        } catch (Exception ex) {
            logger.error("POST operation failed.", ex);
            fail("POST operation failed.");
        }
        if (response == null) {
            fail("Response is null!");
        }

        // ADDED CODE TO CHECK RESPONSE TIME
        logger.debug("AFTER POST:  " + sdf.format((after = System.currentTimeMillis())));
        assertTrue("Call took longer than " + restCallDurationLimit / 1000 + " seconds", (after - before) < restCallDurationLimit);

        // Retrieve request
        try {
            String contentType = response.getHeaders().getFirst("Content-Type");
            if (MediaType.APPLICATION_JSON.equals(contentType) || MediaType.APPLICATION_XML.equals(contentType)) {
                result = response.getEntity();
            } else {
                logger.error("Response body: {}",
                // get response as string
                        response.getEntity(String.class)
                        // improve HTML readability
                                .replaceAll("><", ">\n<"));
                fail("Unexpected content-type: " + contentType);
            }
        } catch (ReaderException ex) {
            response.resetStream();
            logger.error("Bad entity: [{}]", response.getEntity(String.class));
            fail("Bad entity: " + ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unmarshalling failed.", ex);
            fail("Unmarshalling entity failed: " + ex.getMessage());
        }

        assertTrue("The deployment unit was not created successfully.", result.isSuccess());

        return result;
    }

    public void urlsStartScriptProcess(URL deploymentUrl, String user, String password) throws Exception {
        // Remote API setup
        RestRequestHelper requestHelper = RestRequestHelper.newInstance(deploymentUrl, user, password);

        ClientRequest restRequest = requestHelper.createRequest("runtime/" + deploymentId + "/process/" + SCRIPT_TASK_PROCESS_ID
                + "/start");

        // Start process
        ClientResponse<?> responseObj = post(restRequest);
        ProcessInstance procInst = responseObj.getEntity(JaxbProcessInstanceResponse.class).getResult();

        int procStatus = procInst.getState();
        assertEquals("Incorrect process status: " + procStatus, ProcessInstance.STATE_COMPLETED, procStatus);
    }

    public void urlsCloneAndDeployJbpmPlaygroundEvaluationProject() {
        // https://github.com/droolsjbpm/jbpm-playground.git

    }

}