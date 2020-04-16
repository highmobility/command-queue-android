package com.highmobility.commandqueue;

import com.highmobility.autoapi.Command;
import com.highmobility.autoapi.FailureMessage;

import java.util.Calendar;

import javax.annotation.Nullable;

public class QueueItem<T extends Command> {
    Command commandSent;
    Object info;

    boolean timeout;
    Object sdkError;
    FailureMessage.State failure;

    Calendar timeSent;

    public Command getCommandSent() {
        return commandSent;
    }

    public Object getInfo() {
        return info;
    }

    public boolean isTimeout() {
        return timeout;
    }

    public Object getSdkError() {
        return sdkError;
    }

    public FailureMessage.State getFailure() {
        return failure;
    }

    public Calendar getTimeSent() {
        return timeSent;
    }

    public int getRetryCount() {
        return retryCount;
    }

    @Nullable public Class<T> getResponseClass() {
        return responseClass;
    }

    int retryCount;

    @Nullable Class<T> responseClass;

    public QueueItem(Command commandSent, @Nullable Class<T> responseClass) {
        this.commandSent = commandSent;
        this.responseClass = responseClass;
    }

    public QueueItem(Command commandSent, @Nullable Class<T> responseClass, Object info) {
        this.commandSent = commandSent;
        this.responseClass = responseClass;
        this.info = info;
    }
}