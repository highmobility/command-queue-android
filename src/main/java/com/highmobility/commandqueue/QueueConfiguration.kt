package com.highmobility.commandqueue

import com.highmobility.hmkit.Link

/**
 * The queue configuration. Either BleQueueConfiguration or TelematicsQueueConfiguration should be
 * used instead of the base class
 */
open class QueueConfiguration(
    var timeout: Long = 0,
    var retryCount: Int = 0,
    var commandDelay: Int = 0
)

/**
 * The Ble queue configuration.
 *
 * @property timeout Timeout in ms. It's added on top of {@link Link#commandTimeout} as
 *                      an extra buffer. {@link Link#commandTimeout} is the time HMKit waits for an ack.
 *
 * @property retryCount The amount of times a queue item is retried
 * @property commandDelay A delay before sending commands
 */
class BleQueueConfiguration(
    timeout: Long = 0,
    retryCount: Int = 0,
    commandDelay: Int = 0
) : QueueConfiguration(timeout + Link.commandTimeout, retryCount, commandDelay)

/**
 * Timeout for telematics is irrelevant because the request handles the timeout.
 *
 * @param retryCount The amount of times a queue item is retried
 * @param commandDelay A delay before sending commands
 */
class TelematicsQueueConfiguration(
    retryCount: Int = 0,
    commandDelay: Int = 0
) : QueueConfiguration(120000, retryCount, commandDelay)

