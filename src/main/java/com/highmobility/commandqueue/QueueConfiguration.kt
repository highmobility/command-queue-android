package com.highmobility.commandqueue

import com.highmobility.hmkit.Link

/**
 * The queue configuration.
 *
 * @property timeout Timeout in ms. It's added on top of {@link Link#commandTimeout} as
 *                      an extra buffer. {@link Link#commandTimeout} is the time HMKit waits for an ack.
 *
 * @property retryCount The amount of times a queue item is retried
 * @property delayBeforeNextCommands The Delay before sending next items in the queue
 */
open class QueueConfiguration(
    var timeout: Long = 0,
    var retryCount: Int = 0,
    var delayBeforeNextCommands: Int = 0
)

class BleQueueConfiguration(
    timeout: Long = 0,
    retryCount: Int = 0,
    delayBeforeNextCommands: Int = 0
) : QueueConfiguration(timeout + Link.commandTimeout, retryCount, delayBeforeNextCommands)

/**
 * Timeout for telematics is irrelevant because the request handles the timeout.
 */
class TelematicsQueueConfiguration(
    retryCount: Int = 0,
    delayBeforeNextCommands: Int = 0
) : QueueConfiguration(120000, retryCount, delayBeforeNextCommands)

