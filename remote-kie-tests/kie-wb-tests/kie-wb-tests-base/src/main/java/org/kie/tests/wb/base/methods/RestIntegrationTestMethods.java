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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.MediaType;

import org.drools.core.command.runtime.process.StartProcessCommand;
import org.jboss.resteasy.client.ClientRequest;
import org.jboss.resteasy.client.ClientRequestFactory;
import org.jboss.resteasy.client.ClientResponse;
import org.jbpm.process.audit.VariableInstanceLog;
import org.jbpm.services.task.commands.CompleteTaskCommand;
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
import org.kie.api.task.model.TaskSummary;
import org.kie.services.client.api.RemoteRestRuntimeFactory;
import org.kie.services.client.api.command.RemoteRuntimeEngine;
import org.kie.services.client.serialization.JaxbSerializationProvider;
import org.kie.services.client.serialization.JsonSerializationProvider;
import org.kie.services.client.serialization.jaxb.impl.JaxbCommandResponse;
import org.kie.services.client.serialization.jaxb.impl.JaxbCommandsRequest;
import org.kie.services.client.serialization.jaxb.impl.JaxbCommandsResponse;
import org.kie.services.client.serialization.jaxb.impl.JaxbLongListResponse;
import org.kie.services.client.serialization.jaxb.impl.audit.AbstractJaxbHistoryObject;
import org.kie.services.client.serialization.jaxb.impl.audit.JaxbHistoryLogList;
import org.kie.services.client.serialization.jaxb.impl.audit.JaxbVariableInstanceLog;
import org.kie.services.client.serialization.jaxb.impl.process.JaxbProcessInstanceResponse;
import org.kie.services.client.serialization.jaxb.impl.task.JaxbTaskSummaryListResponse;
import org.kie.services.client.serialization.jaxb.rest.JaxbGenericResponse;
import org.kie.tests.wb.base.services.data.JaxbProcessInstanceSummary;
import org.kie.tests.wb.base.test.MyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestIntegrationTestMethods extends AbstractIntegrationTestMethods {

    private static Logger logger = LoggerFactory.getLogger(RestIntegrationTestMethods.class);
    
    private static final String taskUserId = "salaboy";
    
    private final String deploymentId;
    private String mediaType;
    
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
    
    /**
     * Helper methods
     */
    
    private ClientResponse<?> checkResponse(ClientResponse<?> responseObj) throws Exception {
        responseObj.resetStream();
        int status = responseObj.getStatus(); 
        if( status != 200 ) { 
            logger.warn("Response with exception:\n" + responseObj.getEntity(String.class));
            assertEquals( "Status OK", 200, status);
        } 
        return responseObj;
    }
    
    private ClientRequest createRequest(ClientRequestFactory requestFactory, String urlString) { 
        ClientRequest restRequest = requestFactory.createRequest(urlString);
        if( mediaType.equals(MediaType.APPLICATION_XML) ) {
            restRequest.accept(mediaType);
        }
        logger.debug( ">> " + urlString);
        return restRequest;
    }
    
    private void addToRequestBody(ClientRequest restRequest, Object obj) throws Exception { 
        if( mediaType.equals(MediaType.APPLICATION_XML) ) {
            String body = jaxbSerializationProvider.serialize(obj);
            restRequest.body(mediaType, body);
        } else if( mediaType.equals(MediaType.APPLICATION_JSON) ) { 
            String body = jsonSerializationProvider.serialize(obj);
            restRequest.body(mediaType, body);
        }
    }
    
    /**
     * Test methods
     */
    
    public void urlsStartHumanTaskProcess(URL deploymentUrl, ClientRequestFactory requestFactory, ClientRequestFactory taskRequestFactory) throws Exception { 
        // Start process
        String urlString = new URL(deploymentUrl,  deploymentUrl.getPath() + "rest/runtime/"+ deploymentId + "/process/org.jbpm.humantask/start").toExternalForm();
        ClientRequest restRequest = createRequest(taskRequestFactory, urlString);
        ClientResponse<?> responseObj = checkResponse(restRequest.post());
        JaxbProcessInstanceResponse processInstance = (JaxbProcessInstanceResponse) responseObj.getEntity(JaxbProcessInstanceResponse.class);
        long procInstId = processInstance.getId();

        // query tasks for associated task Id
        urlString = new URL(deploymentUrl, deploymentUrl.getPath() + "rest/task/query?status=Reserved&processInstanceId=" + procInstId).toExternalForm();
        restRequest = createRequest(requestFactory, urlString);
        responseObj = checkResponse(restRequest.get());
        
        JaxbTaskSummaryListResponse taskSumlistResponse = (JaxbTaskSummaryListResponse) responseObj.getEntity(JaxbTaskSummaryListResponse.class);
        TaskSummary taskSum = findTaskSummary(procInstId, taskSumlistResponse.getResult());
        long taskId = taskSum.getId();
        
        // get task info
        urlString = new URL(deploymentUrl, deploymentUrl.getPath() + "rest/task/" + taskId).toExternalForm();
        restRequest = createRequest(requestFactory, urlString);
        responseObj = checkResponse(restRequest.get());
        JaxbTask task = (JaxbTask) responseObj.getEntity(JaxbTask.class);
        assertEquals( "Incorrect task id", taskId, task.getId().longValue() );

        // start task
        urlString = new URL(deploymentUrl, deploymentUrl.getPath() + "rest/task/" + taskId + "/start").toExternalForm();
        restRequest = createRequest(taskRequestFactory, urlString);
        responseObj = checkResponse(restRequest.post());
        JaxbGenericResponse resp = (JaxbGenericResponse) responseObj.getEntity(JaxbGenericResponse.class);
        
        // get task info
        urlString = new URL(deploymentUrl, deploymentUrl.getPath() + "rest/task/" + taskId).toExternalForm();
        restRequest = createRequest(requestFactory, urlString);
        responseObj = checkResponse(restRequest.get());
    }
    
    public void commandsStartProcess(URL deploymentUrl, ClientRequestFactory requestFactory) throws Exception { 
        String originalMedia = this.mediaType;
        this.mediaType = MediaType.APPLICATION_XML;
        
        // Start process
        String urlString = new URL(deploymentUrl, deploymentUrl.getPath() + "rest/runtime/" + deploymentId + "/execute").toExternalForm();
        
        ClientRequest restRequest = createRequest(requestFactory, urlString);
        JaxbCommandsRequest commandMessage = new JaxbCommandsRequest(deploymentId, new StartProcessCommand("org.jbpm.humantask"));
        addToRequestBody(restRequest, commandMessage);

        ClientResponse<?> responseObj = checkResponse(restRequest.post());

        JaxbCommandsResponse cmdsResp = (JaxbCommandsResponse) responseObj.getEntity(JaxbCommandsResponse.class);
        long procInstId = ((ProcessInstance) cmdsResp.getResponses().get(0)).getId();

        // query tasks
        restRequest = createRequest(requestFactory, urlString);
        commandMessage = new JaxbCommandsRequest(deploymentId, new GetTasksByProcessInstanceIdCommand(procInstId));
        addToRequestBody(restRequest, commandMessage);

        logger.debug( ">> [getTasksByProcessInstanceId] " + urlString );
        responseObj = checkResponse(restRequest.post());
        JaxbCommandsResponse cmdResponse = (JaxbCommandsResponse) responseObj.getEntity(JaxbCommandsResponse.class);
        List<?> list = (List<?>) cmdResponse.getResponses().get(0).getResult();
        long taskId = (Long) list.get(0);
        
        // start task
        
        logger.debug( ">> [startTask] " + urlString );
        restRequest = requestFactory.createRequest(urlString);
        commandMessage = new JaxbCommandsRequest(new StartTaskCommand(taskId, taskUserId));
        addToRequestBody(restRequest, commandMessage);

        // Get response
        responseObj = checkResponse(restRequest.post());
        responseObj.releaseConnection();

        urlString = new URL(deploymentUrl, deploymentUrl.getPath() + "rest/task/execute").toExternalForm();
        
        restRequest = requestFactory.createRequest(urlString);
        commandMessage = new JaxbCommandsRequest(new CompleteTaskCommand(taskId, taskUserId, null));
        addToRequestBody(restRequest, commandMessage);

        // Get response
        logger.debug( ">> [completeTask] " + urlString );
        checkResponse(restRequest.post());
        
        // TODO: check that above has completed?
        this.mediaType = originalMedia;
    }

    public void remoteApiHumanTaskProcess(URL deploymentUrl, String user, String password) throws Exception {
        // create REST request
        RemoteRestRuntimeFactory restSessionFactory 
            = new RemoteRestRuntimeFactory(deploymentId, deploymentUrl, user, password);
        RuntimeEngine engine = restSessionFactory.newRuntimeEngine();
        KieSession ksession = engine.getKieSession();
        ProcessInstance processInstance = ksession.startProcess(HUMAN_TASK_PROCESS_ID);
        
        logger.debug("Started process instance: " + processInstance + " " + (processInstance == null? "" : processInstance.getId()));
        
        TaskService taskService = engine.getTaskService();
        List<TaskSummary> tasks = taskService.getTasksAssignedAsPotentialOwner(taskUserId, "en-UK");
        long taskId = findTaskId(processInstance.getId(), tasks);
        
        logger.debug("Found task " + taskId);
        Task task = taskService.getTaskById(taskId);
        logger.debug("Got task " + taskId + ": " + task );
        taskService.start(taskId, taskUserId);
        taskService.complete(taskId, taskUserId, null);
        
        logger.debug("Now expecting failure");
        try {
        	taskService.complete(taskId, taskUserId, null);
        	fail( "Should not be able to complete task " + taskId + " a second time.");
        } catch (Throwable t) {
            logger.info("The above exception was an expected part of the test.");
            // do nothing
        }
        
        List<Status> statuses = new ArrayList<Status>();
        statuses.add(Status.Reserved);
        List<TaskSummary> taskIds = taskService.getTasksByStatusByProcessInstanceId(processInstance.getId(), statuses, "en-UK");
        assertEquals("Expected 2 tasks.", 2, taskIds.size());
    }
    
    public void commandsTaskCommands(URL deploymentUrl, ClientRequestFactory requestFactory, String user, String password) throws Exception {
        RuntimeEngine runtimeEngine = new RemoteRestRuntimeFactory(deploymentId, deploymentUrl, user, password).newRuntimeEngine();
        KieSession ksession = runtimeEngine.getKieSession();
        ProcessInstance processInstance = ksession.startProcess(HUMAN_TASK_PROCESS_ID);
        
        long processInstanceId = processInstance.getId();
        JaxbCommandResponse<?> response = executeTaskCommand(deploymentUrl, requestFactory, deploymentId, new GetTasksByProcessInstanceIdCommand(processInstanceId));
        
        long taskId = ((JaxbLongListResponse) response).getResult().get(0);
        assertTrue( "task id is less than 0", taskId > 0 );
        
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("userId", taskUserId);
    }
    
    private JaxbCommandResponse<?> executeTaskCommand(URL deploymentUrl, ClientRequestFactory requestFactory, String deploymentId, Command<?> command) throws Exception {
        String originalMediaType = this.mediaType;
        this.mediaType = MediaType.APPLICATION_XML;
        
        List<Command<?>> commands = new ArrayList<Command<?>>();
        commands.add(command);
        
        String urlString = new URL(deploymentUrl, deploymentUrl.getPath() + "rest/runtime/" + deploymentId + "/execute").toExternalForm();
        logger.info("Client request to: " + urlString);
        ClientRequest restRequest = requestFactory.createRequest(urlString);
        
        JaxbCommandsRequest commandMessage = new JaxbCommandsRequest(commands);
        assertNotNull( "Commands are null!", commandMessage.getCommands() );
        assertTrue( "Commands are empty!", commandMessage.getCommands().size() > 0 );
        
        addToRequestBody(restRequest, commandMessage);

        ClientResponse<JaxbCommandsResponse> responseObj = restRequest.post(JaxbCommandsResponse.class);
        checkResponse(responseObj);
        
        JaxbCommandsResponse cmdsResp = responseObj.getEntity();
        
        this.mediaType = originalMediaType;
        return cmdsResp.getResponses().get(0);
    }
    
    public void urlsHistoryLogs(URL deploymentUrl, ClientRequestFactory requestFactory) throws Exception {
        String urlString = new URL(deploymentUrl,  deploymentUrl.getPath() + "rest/runtime/" + deploymentId + "/process/" + SCRIPT_TASK_VAR_PROCESS_ID + "/start?map_x=initVal").toExternalForm();
        ClientRequest restRequest = requestFactory.createRequest(urlString);

        // Get and check response
        logger.debug( ">> " + urlString );
        ClientResponse<?> responseObj = checkResponse(restRequest.post());
        JaxbProcessInstanceResponse processInstance = (JaxbProcessInstanceResponse) responseObj.getEntity(JaxbProcessInstanceResponse.class);
        long procInstId = processInstance.getId();

        urlString = new URL(deploymentUrl, deploymentUrl.getPath() + "rest/runtime/" + deploymentId + "/history/instance/" + procInstId + "/variable/x").toExternalForm();
        restRequest = requestFactory.createRequest(urlString);
        logger.debug( ">> [history/variables]" + urlString );
        responseObj = checkResponse(restRequest.get());
        JaxbHistoryLogList logList = (JaxbHistoryLogList) responseObj.getEntity(JaxbHistoryLogList.class);
        List<AbstractJaxbHistoryObject> varLogList = logList.getHistoryLogList();
        assertEquals("Incorrect number of variable logs", 4, varLogList.size());
        
        for( AbstractJaxbHistoryObject<?> log : logList.getHistoryLogList() ) {
           JaxbVariableInstanceLog varLog = (JaxbVariableInstanceLog) log;
           assertEquals( "Incorrect variable id", "x", varLog.getVariableId() );
           assertEquals( "Incorrect process id", SCRIPT_TASK_VAR_PROCESS_ID, varLog.getProcessId() );
           assertEquals( "Incorrect process instance id", procInstId, varLog.getProcessInstanceId().longValue() );
        }
    }
 
    public void urlsDataServiceCoupling(URL deploymentUrl, ClientRequestFactory requestFactory, String user) throws Exception {
        String urlString = new URL(deploymentUrl,  deploymentUrl.getPath() + "rest/runtime/" + deploymentId + "/process/" + SCRIPT_TASK_VAR_PROCESS_ID + "/start?map_x=initVal").toExternalForm();
        ClientRequest restRequest = requestFactory.createRequest(urlString);

        // Get and check response
        logger.debug( ">> " + urlString );
        ClientResponse<?> responseObj = checkResponse(restRequest.post());
        JaxbProcessInstanceResponse processInstance = (JaxbProcessInstanceResponse) responseObj.getEntity(JaxbProcessInstanceResponse.class);
        long procInstId = processInstance.getId();

        urlString = new URL(deploymentUrl, deploymentUrl.getPath() + "rest/data/process/instance/" + procInstId ).toExternalForm();
        restRequest = requestFactory.createRequest(urlString);
        logger.debug( ">> [data/process instance]" + urlString );
        responseObj = checkResponse(restRequest.get());
        JaxbProcessInstanceSummary summary = (JaxbProcessInstanceSummary) responseObj.getEntity(JaxbProcessInstanceSummary.class);
        assertEquals("Incorrect initiator.", user, summary.getInitiator());
    }
 
    public void urlsJsonJaxbStartProcess(URL deploymentUrl, ClientRequestFactory requestFactory) throws Exception { 
        // XML
        String urlString = new URL(deploymentUrl,  deploymentUrl.getPath() + "rest/runtime/"+ deploymentId + "/process/org.jbpm.humantask/start").toExternalForm();
        ClientRequest restRequest = requestFactory.createRequest(urlString);
        logger.debug( ">> " + urlString);
        ClientResponse<?> responseObj = checkResponse(restRequest.post());
        String result = (String) responseObj.getEntity(String.class);
        assertTrue("Doesn't start like a JAXB string!", result.startsWith("<"));

        // JSON
        restRequest = requestFactory.createRequest(urlString);
        restRequest.accept(MediaType.APPLICATION_JSON_TYPE);
        logger.debug( ">> " + urlString);
        responseObj = checkResponse(restRequest.post());
        result = (String) responseObj.getEntity(String.class);
        assertTrue("Doesn't start like a JSON string!", result.startsWith("{"));
    }
    
    public void urlsHumanTaskWithFormVariableChange(URL deploymentUrl, ClientRequestFactory requestFactory) throws Exception { 
        String originalMedia = this.mediaType;
        this.mediaType = MediaType.APPLICATION_XML;
        
        // Start process
        String urlString = new URL(deploymentUrl, 
                deploymentUrl.getPath() + "rest/runtime/" + deploymentId + "/process/" + HUMAN_TASK_VAR_PROCESS_ID + "/start?map_userName=John"
                ).toExternalForm();
        
        ClientRequest restRequest = createRequest(requestFactory, urlString);
        ClientResponse<?> responseObj = checkResponse(restRequest.post());
        JaxbProcessInstanceResponse processInstance = (JaxbProcessInstanceResponse) responseObj.getEntity(JaxbProcessInstanceResponse.class);
        long procInstId = processInstance.getId();

        // query tasks for associated task Id
        urlString = new URL(deploymentUrl, deploymentUrl.getPath() + "rest/task/query?processInstanceId=" + procInstId).toExternalForm();
        restRequest = createRequest(requestFactory, urlString);
        responseObj = checkResponse(restRequest.get());
        
        JaxbTaskSummaryListResponse taskSumlistResponse = (JaxbTaskSummaryListResponse) responseObj.getEntity(JaxbTaskSummaryListResponse.class);
        TaskSummary taskSum = findTaskSummary(procInstId, taskSumlistResponse.getResult());
        long taskId = taskSum.getId();
        
        // start task
        urlString = new URL(deploymentUrl, deploymentUrl.getPath() + "rest/task/" + taskId + "/start").toExternalForm();
        restRequest = createRequest(requestFactory, urlString);
        responseObj = checkResponse(restRequest.post());
        JaxbGenericResponse resp = (JaxbGenericResponse) responseObj.getEntity(JaxbGenericResponse.class);
        
        // complete task
        String georgeVal = "George";
        urlString = new URL(deploymentUrl, deploymentUrl.getPath() + "rest/task/" + taskId + "/complete?map_outUserName=" + georgeVal).toExternalForm();
        restRequest = createRequest(requestFactory, urlString);
        responseObj = checkResponse(restRequest.post());
        resp = (JaxbGenericResponse) responseObj.getEntity(JaxbGenericResponse.class);
        
        urlString = new URL(deploymentUrl, 
                    deploymentUrl.getPath() + "rest/runtime/" + deploymentId + "/history/instance/" + procInstId + "/variable/userName").toExternalForm();
        restRequest = createRequest(requestFactory, urlString);
        responseObj = checkResponse(restRequest.get());
        JaxbHistoryLogList histResp = (JaxbHistoryLogList) responseObj.getEntity(JaxbHistoryLogList.class);
        
        List<AbstractJaxbHistoryObject> histList = histResp.getHistoryLogList();
        boolean georgeFound = false;
        for( AbstractJaxbHistoryObject<VariableInstanceLog> absVarLog : histList ) { 
            VariableInstanceLog varLog = ((JaxbVariableInstanceLog) absVarLog).getResult();
            if( "userName".equals(varLog.getVariableId()) && georgeVal.equals(varLog.getValue()) ) { 
                georgeFound = true;
            }
        }
        assertTrue("'userName' var with value '" + georgeVal + "' not found!", georgeFound);
        
        this.mediaType = originalMedia;
        
    }
    
    public void urlsHttpURLConnectionAcceptHeaderIsFixed(URL deploymentUrl, ClientRequestFactory requestFactory) throws Exception { 
        URL url = new URL(deploymentUrl, deploymentUrl.getPath() + "rest/runtime/" + deploymentId + "/process/" + SCRIPT_TASK_PROCESS_ID + "/start");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");

        connection.connect();
        assertEquals(200, connection.getResponseCode());
    }
 
    public void remoteApiSerialization(URL deploymentUrl, ClientRequestFactory requestFactory, String user, String password) throws Exception { 
        RemoteRestRuntimeFactory restSessionFactory 
        = new RemoteRestRuntimeFactory(deploymentId, deploymentUrl, user, password);
        RuntimeEngine engine = restSessionFactory.newRuntimeEngine();
        KieSession ksession = engine.getKieSession();
        ProcessInstance processInstance = ksession.startProcess(SCRIPT_TASK_PROCESS_ID);
        Collection<ProcessInstance> processInstances = ksession.getProcessInstances();
    }

    public void remoteApiExtraJaxbClasses(URL deploymentUrl, String user, String password) throws Exception { 
        // create REST request
        RemoteRestRuntimeFactory restSessionFactory 
            = new RemoteRestRuntimeFactory(deploymentId, deploymentUrl, user, password);
        RemoteRuntimeEngine engine = restSessionFactory.newRuntimeEngine();
        
        KieSession ksession = engine.getKieSession();
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("myobject", new MyType("Hello World!"));
        long procInstId = ksession.startProcess(OBJECT_VARIABLE_PROCESS_ID, parameters).getId();
        
        List<VariableInstanceLog> varLogList = engine.getAuditLogService().findVariableInstancesByName("type", false);
        VariableInstanceLog thisProcInstVarLog = null;
        for( VariableInstanceLog varLog : varLogList ) {
            if( varLog.getProcessInstanceId() == procInstId ) { 
                thisProcInstVarLog = varLog;
            }
        }
        assertEquals( "type", thisProcInstVarLog.getVariableId() );
        logger.info("'type' var value: " + thisProcInstVarLog.getValue() );
        
        parameters.clear(); 
        parameters.put("myobject", new Float[]{0.2F});
        procInstId = ksession.startProcess(OBJECT_VARIABLE_PROCESS_ID, parameters).getId();
        
        varLogList = engine.getAuditLogService().findVariableInstancesByName("type", false);
        thisProcInstVarLog = null;
        for( VariableInstanceLog varLog : varLogList ) {
            if( varLog.getProcessInstanceId() == procInstId ) { 
                thisProcInstVarLog = varLog;
            }
        }
        assertEquals( "type", thisProcInstVarLog.getVariableId() );
        logger.info("'type' var value: " + thisProcInstVarLog.getValue() );
    }
    
}