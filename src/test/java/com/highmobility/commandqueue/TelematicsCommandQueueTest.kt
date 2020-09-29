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
package com.highmobility.commandqueue

import com.highmobility.autoapi.Command
import com.highmobility.autoapi.Doors
import com.highmobility.autoapi.value.LockState
import com.highmobility.hmkit.error.TelematicsError
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TelematicsCommandQueueTest {
    val responseCommand = arrayOfNulls<Command>(1)
    val commandsSent = intArrayOf(0)

    val failure = arrayOfNulls<QueueItemFailure>(1)
    val iQueue: ICommandQueue = object : ICommandQueue {
        override fun onCommandReceived(
            command: Command,
            sentCommand: QueueItem<*>?
        ) {
            responseCommand[0] = command
        }

        override fun onCommandFailed(reason: QueueItemFailure) {
            failure[0] = reason
        }

        override fun sendCommand(command: Command) {
            commandsSent[0]++
        }
    }

    @Before
    fun prepare() {
        responseCommand[0] = null
        commandsSent[0] = 0
    }

    @Test
    fun retriesOn408() {
        val queue = TelematicsCommandQueue(iQueue, TelematicsQueueConfiguration(1))

        val command: Command = Doors.LockUnlockDoors(LockState.LOCKED)
        val error = TelematicsError(TelematicsError.Type.HTTP_ERROR, 408, "Timeout")

        queue.queue(command)
        assertEquals(1, commandsSent[0])

        Thread.sleep(40)
        queue.onCommandFailedToSend(command, error)
        assertEquals(2, commandsSent[0])

        Thread.sleep(40)
        queue.onCommandFailedToSend(command, error)
        Thread.sleep(40) // command is retried 1 times

        assertEquals(2, commandsSent[0])

        assertSame(failure[0]!!.getReason(), QueueItemFailure.Reason.TIMEOUT)
        val telematicsError = failure[0]!!.getErrorObject() as TelematicsError
        assertSame(telematicsError.type, TelematicsError.Type.HTTP_ERROR)
        assertTrue(telematicsError.code == 408)
    }
}