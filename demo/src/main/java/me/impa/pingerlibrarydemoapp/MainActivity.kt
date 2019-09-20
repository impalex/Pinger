/*
 * Copyright (c) 2019 Alexander Yaburov
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package me.impa.pingerlibrarydemoapp

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import kotlinx.android.synthetic.main.activity_main.*
import me.impa.pinger.PingInfo
import me.impa.pinger.Pinger
import java.lang.Exception

class MainActivity : AppCompatActivity() {

    private var pinger: Pinger? = null;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        pingbutton.setOnClickListener { onButtonClick() }
        pinglog.setMovementMethod(ScrollingMovementMethod())
    }

    fun addToLog(line: String) {
        runOnUiThread {
            pinglog.text = "${pinglog.text}\n$line";
            val scrollAmount = pinglog.layout.getLineTop(pinglog.lineCount) - pinglog.height
            pinglog.scrollTo(0, 0.coerceAtLeast(scrollAmount))
        }
    }

    fun Stop() {
        pinger?.StopAll()
        pinger = null
        runOnUiThread {
            pingbutton.text = "Start"
        }
    }

    fun onButtonClick() {

        val ping = Pinger()
        ping.setOnPingListener(object : Pinger.OnPingListener {
            override fun OnStart(pingInfo: PingInfo) {
                Log.i("PING", "Pinging ${pingInfo.ReverseDns} [${pingInfo.RemoteIp}]")
            }

            override fun OnStop(pingInfo: PingInfo) {
                Log.i("PING", "Ping complete")
            }

            override fun OnException(pingInfo: PingInfo, e: Exception, isFatal: Boolean) {
            }

            override fun OnTimeout(pingInfo: PingInfo, sequence: Int) {
                Log.i("PING", "#$sequence: Timeout!")
                if (sequence>=10)
                    pingInfo.Pinger.Stop(pingInfo.PingId)
            }

            override fun OnReplyReceived(
                pingInfo: PingInfo,
                sequence: Int,
                timeMs: Int
            ) {
                Log.i("PING", "#$sequence: Reply from ${pingInfo.RemoteIp}: bytes=${pingInfo.Size} time=$timeMs TTL=${pingInfo.Ttl}")
                if (sequence>=10)
                    pingInfo.Pinger.Stop(pingInfo.PingId)
            }

            override fun OnSendError(pingInfo: PingInfo, sequence: Int) {
            }
        })

        ping.Ping("google.com")

        if (pinger != null) {
            Stop()
            return
        }

        pinger = Pinger();
        pinger?.setOnPingListener(object : Pinger.OnPingListener {
            override fun OnTimeout(pingInfo: PingInfo, sequence: Int) {
                addToLog("#$sequence: Timeout!")
                if (sequence>=10)
                    Stop()
            }

            override fun OnReplyReceived(pingInfo: PingInfo, sequence: Int, timeMs: Int) {
                addToLog("#$sequence: Reply from ${pingInfo.RemoteIp}: bytes=${pingInfo.Size} time=$timeMs TTL=${pingInfo.Ttl}")
                if (sequence>=10)
                    Stop()
            }

            override fun OnSendError(pingInfo: PingInfo, sequence: Int) {
                addToLog("#$sequence: PING error!")
            }

            override fun OnStop(pingInfo: PingInfo) {
                addToLog("Ping complete!")
            }

            override fun OnStart(pingInfo: PingInfo) {
                addToLog("Pinging ${pingInfo.ReverseDns} [${pingInfo.RemoteIp}] with ${pingInfo.Size} bytes of data:")
            }

            override fun OnException(pingInfo: PingInfo, e: Exception, isFatal: Boolean) {
                addToLog("$e")
                if (isFatal)
                    Stop()
            }

        })
        pinger?.Ping(hostname.text.toString(), 500, 500, 64, 60, byteArrayOf(0,1,2,3,4,5,6,7,8,9,10))
        pingbutton.text = "Stop"

    }
}
