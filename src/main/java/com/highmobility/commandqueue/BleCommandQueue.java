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

import com.highmobility.autoapi.Command;
import com.highmobility.hmkit.Link;
import com.highmobility.hmkit.error.LinkError;
import com.highmobility.value.Bytes;

/**
 * Queue system for BLE commands that is meant to be used as a layer between the app and HMKit. An
 * item will wait for its ack ({@link #queue(Command)}) or for its response command ({@link
 * #queue(Command, Class)}) before next items will be sent. Ack timeout will come from the sdk.
 * Response command timeout is handled by this class(hence extraTimeout in configuration).
 * <p>
 * Command will succeed after ack or the expected response command via {@link ICommandQueue}
 * <p>
 * For this to work, command responses have to be dispatched to:
 * <ul>
 * <li>{@link #onCommandReceived(Bytes)}</li>
 * <li>{@link #onCommandSent(Command)} (ack)</li>
 * <li>{@link #onCommandFailedToSend(Command, LinkError)}</li>
 * </ul>
 * <p>
 * Be aware:
 * <ul>
 * <li> Command will fail without retrying if the SDK returns a LinkError or the response is a
 * Failure command. The queue will be cleared then as well.</li>
 * <li>Commands will be timed out after {@link Link#commandTimeout} + configuration.extraTimeout.</li>
 * <li>Commands will be tried again for {@link #retryCount} times. Commands with same type will not
 * be queued.</li>
 * </ul>
 * Call {@link #purge()} to clear the queue when link is lost.
 */
public class BleCommandQueue extends CommandQueue {

    /**
     * Create the queue with default 45s extra timeout and 3x retry count.
     *
     * @param listener The queue listener.
     */
    public BleCommandQueue(IBleCommandQueue listener) {
        this(listener, new BleQueueConfiguration(45000, 3, 0));
    }

    /**
     * @param listener      The queue listener.
     * @param configuration The queue configuration
     */
    public BleCommandQueue(IBleCommandQueue listener, BleQueueConfiguration configuration) {
        super(listener, configuration);
    }

    /**
     * Queue the command and wait for its response command.
     *
     * @param command      The command that will be queued.
     * @param responseType The command's response type
     * @return false if cannot queue at this time - maybe this command type is already queued.
     */
    public <T extends Command> boolean queue(Command command, Class<T> responseType) {
        if (typeAlreadyQueued(command)) return false;
        QueueItem item = new QueueItem(command, responseType);
        items.add(item);
        sendItem();
        return true;
    }

    /**
     * Queue the command and wait for its response command.
     *
     * @param command      The command that will be queued.
     * @param responseType The command's response type
     * @param info         Some info that will be retained in the QueueItem
     * @return false if cannot queue at this time - maybe this command type is already queued.
     */
    public <T extends Command> boolean queue(Command command, Class<T> responseType, Object info) {
        if (typeAlreadyQueued(command)) return false;
        QueueItem item = new QueueItem(command, responseType, info);
        items.add(item);
        sendItem();
        return true;
    }

    /**
     * Call after {@link Link.CommandCallback#onCommandSent()}.
     *
     * @param command The command that was sent.
     */
    public void onCommandSent(Command command) {
        // we only care about first item in queue
        if (items.size() == 0) return;
        QueueItem item = items.get(0);

        if (isSameCommand(item.commandSent, command)) {
            // if only waiting for an ack then finish the item
            ((IBleCommandQueue) listener).onCommandAck(item.commandSent);
            if (item.responseClass == null) {
                items.remove(0);
                sendItem();
            }
        }
    }

    /**
     * Call after {@link Link.CommandCallback#onCommandFailed(LinkError)}.
     *
     * @param command The command that was sent.
     */
    public void onCommandFailedToSend(Command command, LinkError error) {
        boolean timeout = error.getType() == LinkError.Type.TIME_OUT;
        super.onCommandFailedToSend(command, error, timeout);
    }
}
