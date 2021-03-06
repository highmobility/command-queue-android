/*
 * The MIT License
 *
 * Copyright (c) 2014- High-Mobility GmbH (https://high-mobility.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.highmobility.commandqueue;

import android.os.Handler;

import com.highmobility.autoapi.Command;
import com.highmobility.autoapi.CommandResolver;
import com.highmobility.autoapi.FailureMessage;
import com.highmobility.value.Bytes;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static timber.log.Timber.d;

/**
 * This is the Command queue base class. Depending on the environment, the subclasses {@link
 * BleCommandQueue} or {@link TelematicsCommandQueue} should be used instead of this class.
 */
public class CommandQueue {
    long commandDelay = 0;

    ICommandQueue listener;
    long timeout;
    int retryCount;
    ArrayList<QueueItem> items = new ArrayList<>();

    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    ScheduledFuture<?> retryHandle;

    CommandQueue(ICommandQueue listener, QueueConfiguration configuration) {
        this.listener = listener;
        this.timeout = configuration.getTimeout();
        this.retryCount = configuration.getRetryCount();
        this.commandDelay = configuration.getCommandDelay();
        CommandResolver.setRuntime(CommandResolver.RunTime.ANDROID);
    }

    /**
     * Queue the command and wait for its ack.
     *
     * @param command The command will be queued.
     * @return false if cannot queue at this time - maybe this command type is already queued.
     */
    public boolean queue(Command command) {
        if (typeAlreadyQueued(command)) return false;
        QueueItem item = new QueueItem(command, null);
        items.add(item);
        sendItem();
        return true;
    }

    /**
     * Clear the queue and timers. Call when connection is lost.
     */
    public void purge() {
        items.clear();
        stopTimer();
    }

    public void onCommandReceived(Bytes commandBytes) {
        Command command = CommandResolver.resolve(commandBytes);

        // queue is empty
        if (items.size() == 0) {
            listener.onCommandReceived(command, null);
            return;
        }

        // we only care about first item in queue.
        QueueItem item = items.get(0);

        if (command instanceof FailureMessage.State) {
            FailureMessage.State failure = (FailureMessage.State) command;

            if (failure.getCommandFailed(item.commandSent.getIdentifier(),
                    item.commandSent.getCommandType())) {
                item.failure = failure;
                failItem();
            }
        } else if (command.getLength() > 2) {
            // for telematics, there are only responses for sent commands. No random incoming
            // commands.
            if (this instanceof TelematicsCommandQueue || command.getClass() == item.responseClass) {
                // received a command of expected type
                listener.onCommandReceived(command, item);
                items.remove(0);
                sendItem();
            } else {
                listener.onCommandReceived(command, null);
            }
        } else {
            listener.onCommandReceived(command, null);
        }
    }

    boolean typeAlreadyQueued(Command command) {
        for (int i = 0; i < items.size(); i++) {
            QueueItem item = items.get(i);
            if (isSameCommand(item.commandSent, command)) return true;
        }
        return false;
    }

    boolean isSameCommand(Command firstCommand, Command secondCommand) {
        return (firstCommand.getIdentifier() == secondCommand.getIdentifier() &&
                firstCommand.getCommandType() == secondCommand.getCommandType());
    }

    void sendItem() {
        if (items.size() == 0) return;
        QueueItem item = items.get(0);

        if (item.timeSent == null) {
            item.timeSent = Calendar.getInstance();

            if (commandDelay != 0) {
                new Handler().postDelayed(() -> {
                    listener.sendCommand(item.commandSent);
                }, commandDelay);
            } else {
                listener.sendCommand(item.commandSent);
            }

            startTimer();
        }
    }

    void onCommandFailedToSend(Command command, Object error, boolean timeout) {
        // retry only if timeout, otherwise go straight to failure.
        if (items.size() == 0) return;
        QueueItem item = items.get(0);
        if (isSameCommand(item.commandSent, command) == false) return;

        item.sdkError = error;
        if (timeout && item.retryCount < retryCount) {
            item.timeout = true;
            item.retryCount++;
            item.timeSent = null;
            sendItem();
        } else {
            failItem();
        }
    }

    void failItem() {
        if (items.size() == 0) return;
        QueueItem item = items.get(0);

        QueueItemFailure.Reason reason;
        if (item.failure != null) {
            reason = QueueItemFailure.Reason.FAILURE_RESPONSE;
        } else if (item.sdkError != null && item.timeout == false) {
            reason = QueueItemFailure.Reason.FAILED_TO_SEND;
        } else {
            reason = QueueItemFailure.Reason.TIMEOUT;
        }

        items.remove(item);
        QueueItemFailure failure = new QueueItemFailure(item, reason, item.failure, item.sdkError);
        purge();
        listener.onCommandFailed(failure);
    }

    void startTimer() {
        // start if not running already
        if (retryHandle == null || retryHandle.isDone()) {
            retryHandle = scheduler.scheduleAtFixedRate(retry, 0, 30, TimeUnit.MILLISECONDS);
        }
    }

    void stopTimer() {
        // stop if queue empty
        if (items.size() == 0) {
            if (retryHandle != null) retryHandle.cancel(true);
        }
    }

    final Runnable retry = () -> sendCommandAgainIfTimeout();

    void sendCommandAgainIfTimeout() {
        if (items.size() > 0) {
            QueueItem item = items.get(0);
            long now = Calendar.getInstance().getTimeInMillis();
            long sent = item.timeSent.getTimeInMillis();

            if (now - sent > timeout) {
                item.timeSent = null;
                item.retryCount++;

                if (item.retryCount > retryCount) {
                    failItem();
                } else {
                    sendItem();
                }
            }
        } else {
            stopTimer();
        }
    }
}