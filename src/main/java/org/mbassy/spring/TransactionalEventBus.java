package org.mbassy.spring;

import org.mbassy.IMessageBus;
import org.mbassy.IPublicationErrorHandler;
import org.mbassy.MBassador;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.concurrent.Executor;

/**
 * User: benni
 * Date: 11/12/12
 */
public class TransactionalEventBus<T> implements IMessageBus<T, ITransactionalPostCommand>{

    private MBassador<T> internalBus = new MBassador<T>();


    @Override
    public void subscribe(Object listener) {
        internalBus.subscribe(listener);
    }

    @Override
    public void unsubscribe(Object listener) {
        internalBus.unsubscribe(listener);
    }

    public ITransactionalPostCommand post(T event){
         return new TransactionalPostCommand(event);
    }

    @Override
    public void addErrorHandler(IPublicationErrorHandler errorHandler) {
        internalBus.addErrorHandler(errorHandler);
    }

    @Override
    public Collection<IPublicationErrorHandler> getRegisteredErrorHandlers() {
        return internalBus.getRegisteredErrorHandlers();
    }

    @Override
    public Executor getExecutor() {
        return internalBus.getExecutor();
    }

    private static enum Triggered {
        Before, After;
    }

    public class DispatchingSynchronization implements TransactionSynchronization{

        private Transaction.GenericTransaction txConfig;
        private T message;
        private Triggered when;

        private DispatchingSynchronization(Transaction.GenericTransaction txConfig, T message, Triggered when) {
            this.txConfig = txConfig;
            this.message = message;
            this.when = when;
        }

        private Transaction.Status fromSpringStatusIdentifier(int status){
            switch (status){
                case STATUS_COMMITTED: return Transaction.Status.Committed;
                case STATUS_ROLLED_BACK: return Transaction.Status.RolledBack;
                case STATUS_UNKNOWN: return Transaction.Status.Unknown;
            }
            return Transaction.Status.Unknown;
        }

        @Override
        public void suspend() {
            //nothing
        }

        @Override
        public void resume() {
            //nothing
        }

        @Override
        public void flush() {
            //nothing
        }

        @Override
        public void beforeCommit(boolean readOnly) {
            if(Triggered.Before.equals(when) && Transaction.Phase.Commit.equals(txConfig.getPhase())){
                dispatchMessage();
            }
        }

        @Override
        public void beforeCompletion() {
            if(Triggered.Before.equals(when) && Transaction.Phase.Completion.equals(txConfig.getPhase())){
                dispatchMessage();
            }
        }

        @Override
        public void afterCommit() {
            if(Triggered.After.equals(when) && Transaction.Phase.Commit.equals(txConfig.getPhase())){
                dispatchMessage();
            }
        }

        @Override
        public void afterCompletion(int status) {
            if(Triggered.After.equals(when) && Transaction.Phase.Completion.equals(txConfig.getPhase())
                    && (Transaction.Status.Any.equals(txConfig.getStatus()) || fromSpringStatusIdentifier(status).equals(txConfig.getStatus()))){
               dispatchMessage();
            }
        }

        private void dispatchMessage(){
            internalBus.publish(message);
        }
    }


    public class TransactionalPostCommand implements ITransactionalPostCommand {

        private T message;

        public TransactionalPostCommand(T message) {
            this.message = message;
        }

        public void now(){
            internalBus.publish(message);
        }

        public void asynchronously(){
            internalBus.publishAsync(message);
        }

        @Override
        public void after(Transaction.GenericTransaction txConfig){
            TransactionSynchronizationManager.registerSynchronization(new DispatchingSynchronization(txConfig, message, Triggered.After));
        }

        @Override
        public void before(Transaction.UnparametrizedTransaction txConfig) {
            TransactionSynchronizationManager.registerSynchronization(new DispatchingSynchronization(txConfig, message, Triggered.Before));
        }


    }


}