package org.jason.server.utils

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ThreadPool private constructor() {
    private lateinit var key: Any
    private var executorService = Executors.newFixedThreadPool(7)
    var isActive: Boolean = true

    companion object {
        private val hashMap = HashMap<Any, ThreadPool>()
        fun instance(key: Any): ThreadPool {
            return hashMap[key] ?: let {
                ThreadPool().apply {
                    this.key = key
                }.also {
                    hashMap[key] = it
                }
            }
        }
    }

    fun setThreads(threads: Int) {
        executorService = Executors.newFixedThreadPool(threads)
    }

    fun addTask(task: Runnable): ThreadPool {
        try {
            executorService.submit(task)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return this
    }

    fun addTask(task: () -> Unit): ThreadPool {
        try {
            executorService.submit(task)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return this
    }

    fun shutdown(): ThreadPool {
        executorService.shutdown() //不再接收新的任务
        return this
    }

    fun cancel() {
        isActive = false
        executorService.shutdownNow()
    }

    fun onFinished(call: () -> Unit) {
        executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)
        call.invoke()
    }
}