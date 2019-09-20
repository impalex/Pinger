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

package me.impa.pinger;

import android.util.SparseArray;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicInteger;
import androidx.annotation.NonNull;

public class Pinger {

    private OnPingListener onPingListener;

    private static final int SEND_ERROR = -1;
    private static final int SEND_TIMEOUT = -2;
    public static final int DEFAULT_TIMEOUT = 1000;
    public static final int DEFAULT_SLEEP = 1000;
    public static final int DEFAULT_TTL = 48;
    public static final int DEFAULT_SIZE = 32;

    private AtomicInteger lastId = new AtomicInteger(0);
    private SparseArray<Thread> pingThreads = new SparseArray<>();

    public void setOnPingListener(OnPingListener onPingListener) {
        this.onPingListener = onPingListener;
    }

    public int Ping(String host) {
        return this.Ping(host, DEFAULT_TIMEOUT, DEFAULT_SLEEP, DEFAULT_TTL, DEFAULT_SIZE, null);
    }

    public int Ping(final String host, final int timeout, final int sleep, final int ttl, final int size, final byte[] pattern) {

        int pingId = lastId.addAndGet(1);
        Thread pingThread = new Thread(new PingRunnable(this, pingId, host, timeout, sleep, ttl, size, pattern));
        pingThreads.append(pingId, pingThread);
        pingThread.start();

        return pingId;
    }

    public void Stop(int pingId) {
        Thread thread = pingThreads.get(pingId);
        if (thread != null)
            thread.interrupt();
        pingThreads.remove(pingId);
    }

    public void StopAll() {
        for (int i = 0, size = pingThreads.size(); i < size; i++) {
            Thread thread = pingThreads.valueAt(i);
            thread.interrupt();
        }
        pingThreads.clear();
    }

    private native int createicmpsocket(String host, int timeout, int ttl);
    private native void closeicmpsocket(int sock);
    private native int ping(int sock, char sequence, int size, byte[] pattern);

    static {
        System.loadLibrary("icmp_util");
    }

    private class PingRunnable implements Runnable {

        private String host;
        private int timeout;
        private int sleep;
        private int ttl;
        private int size;
        private int id;
        private byte[] pattern;
        private Pinger pinger;

        PingRunnable(final Pinger pinger, final int id, final String host, final int timeout, final int sleep, final int ttl, final int size, final byte[] pattern) {
            this.pinger = pinger;
            this.id = id;
            this.host = host;
            this.timeout = timeout;
            this.sleep = sleep;
            this.ttl = ttl;
            this.size = size;
            this.pattern = pattern == null ? new byte[]{} : pattern;
        }

        @Override
        public void run() {
            InetAddress address;
            String hostAddress, reverseName;
            final PingInfo pingInfo = new PingInfo();
            pingInfo.Pinger = pinger;
            pingInfo.PingId = id;
            pingInfo.Size = size;
            pingInfo.Timeout = timeout;
            pingInfo.Ttl = ttl;
            pingInfo.RemoteHost = host;
            try {
                address = Inet4Address.getByName(host);
                hostAddress = address.getHostAddress();
                reverseName = Inet4Address.getByName(hostAddress).getCanonicalHostName();
                pingInfo.RemoteIp = hostAddress;
                pingInfo.ReverseDns = reverseName;
            } catch (UnknownHostException e) {
                if (onPingListener != null)
                    onPingListener.OnException(pingInfo, e, true);
                return;
            }

            final int sock = createicmpsocket(hostAddress, timeout, ttl);
            if (sock <= 0) {
                if (onPingListener != null)
                    onPingListener.OnException(pingInfo, new SocketException(), true);
                return;
            }
            if (onPingListener != null)
                onPingListener.OnStart(pingInfo);
            char seq = 1;
            try {
                //noinspection InfiniteLoopStatement
                while (true) {
                    try {
                        int response = ping(sock, seq, size, pattern == null ? new byte[]{} : pattern);
                        if (onPingListener != null) {
                            if (response >= 0)
                                onPingListener.OnReplyReceived(pingInfo, seq, response);
                            else if (response == SEND_ERROR)
                                onPingListener.OnSendError(pingInfo, seq);
                            else if (response == SEND_TIMEOUT)
                                onPingListener.OnTimeout(pingInfo, seq);
                        }
                        Thread.sleep(sleep);
                        seq++;
                    }
                    catch (InterruptedException e) {
                        throw e;
                    }
                    catch (Exception e) {
                        if (onPingListener != null)
                            onPingListener.OnException(pingInfo, e, false);
                    }
                }

            } catch (InterruptedException e) {
                // it's ok
            }
            if (onPingListener != null)
                onPingListener.OnStop(pingInfo);
            closeicmpsocket(sock);

        }
    }

    public interface OnPingListener {
        void OnStart(@NonNull PingInfo pingInfo);

        void OnStop(@NonNull PingInfo pingInfo);

        void OnSendError(@NonNull PingInfo pingInfo, int sequence);

        void OnReplyReceived(@NonNull PingInfo pingInfo, int sequence, int timeMs);

        void OnTimeout(@NonNull PingInfo pingInfo, int sequence);

        void OnException(@NonNull PingInfo pingInfo, @NonNull Exception e, boolean isFatal);
    }
}
