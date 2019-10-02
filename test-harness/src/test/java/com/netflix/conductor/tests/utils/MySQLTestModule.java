package com.netflix.conductor.tests.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.utils.ExternalPayloadStorage;
import com.netflix.conductor.common.utils.JsonMapperProvider;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.config.CoreModule;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.execution.WorkflowStatusListener;
import com.netflix.conductor.core.execution.WorkflowStatusListenerStub;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.IndexDAO;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.dao.dynomite.RedisExecutionDAO;
import com.netflix.conductor.dao.dynomite.RedisMetadataDAO;
import com.netflix.conductor.dao.dynomite.queue.DynoQueueDAO;
import com.netflix.conductor.dao.mysql.EmbeddedDatabase;
import com.netflix.conductor.dao.mysql.MySQLExecutionDAO;
import com.netflix.conductor.dao.mysql.MySQLMetadataDAO;
import com.netflix.conductor.dao.mysql.MySQLQueueDAO;
import com.netflix.conductor.mysql.MySQLConfiguration;
import com.netflix.conductor.mysql.MySQLDataSourceProvider;
import com.netflix.conductor.mysql.SystemPropertiesMySQLConfiguration;
import com.netflix.conductor.service.MetadataService;
import com.netflix.conductor.service.MetadataServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author jvemugunta
 */
public class MySQLTestModule extends AbstractModule {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private int maxThreads = 50;

    private ExecutorService executorService;
    protected final EmbeddedDatabase DB = EmbeddedDatabase.INSTANCE;

    @Override
    protected void configure() {


        bind(Configuration.class).to(SystemPropertiesMySQLConfiguration.class).in(Singleton.class);
        bind(MySQLConfiguration.class).to(SystemPropertiesMySQLConfiguration.class).in(Singleton.class);

        bind(DataSource.class).toProvider(MySQLDataSourceProvider.class).in(Scopes.SINGLETON);
        bind(new TypeLiteral<MetadataDAO<TaskDef, WorkflowDef, EventHandler>>() {}).to(MySQLMetadataDAO.class);
        bind(new TypeLiteral<ExecutionDAO<Task, Workflow>>() {}).to(MySQLExecutionDAO.class);
        bind(new TypeLiteral<QueueDAO<Message>>() {}).to(MySQLQueueDAO.class);
        bind(IndexDAO.class).to(MockIndexDAO.class);
        bind(WorkflowStatusListener.class).to(WorkflowStatusListenerStub.class);

        install(new CoreModule());
        bind(UserTask.class).asEagerSingleton();
        bind(ObjectMapper.class).toProvider(JsonMapperProvider.class);
        bind(ExternalPayloadStorage.class).to(MockExternalPayloadStorage.class);

        bind(new TypeLiteral<MetadataService<TaskDef, WorkflowDef, EventHandler>>() {}).to(MetadataServiceImpl.class);
    }


    @Provides
    public ExecutorService getExecutorService() {
        return this.executorService;
    }

    private void configureExecutorService() {
        AtomicInteger count = new AtomicInteger(0);
        this.executorService = java.util.concurrent.Executors.newFixedThreadPool(maxThreads, runnable -> {
            Thread workflowWorkerThread = new Thread(runnable);
            workflowWorkerThread.setName(String.format("workflow-worker-%d", count.getAndIncrement()));
            return workflowWorkerThread;
        });
    }
}
