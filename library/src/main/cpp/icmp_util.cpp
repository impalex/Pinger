#pragma clang diagnostic push
#pragma ide diagnostic ignored "cppcoreguidelines-avoid-magic-numbers"
//
// Created by impa on 18.09.2019.
//

#include "icmp_util.h"

#include <linux/icmp.h>
#include <jni.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <ctime>
#include <unistd.h>
#include <malloc.h>
#include <map>

std::map<int, sockaddr_in> addr_map;

#pragma clang diagnostic push
#pragma ide diagnostic ignored "hicpp-signed-bitwise"
#pragma ide diagnostic ignored "readability-magic-numbers"
u_short checksum(u_short *buf, int len) {

    u_long sum = 0;

    while (len>1) {
        sum += *buf++;
        len -= sizeof(u_short);
    }

    if (len)
        sum += *(u_char*)buf;

    sum = (sum >> 16) + (sum & 0xffff);
    sum += (sum >> 16);
    return (u_short)(~sum);
}
#pragma clang diagnostic pop

int create_icmp_socket(const char *host, int timeout_ms, int ttl) {

    struct timeval t_out;
    t_out.tv_sec = MS_TO_SEC(timeout_ms);
    t_out.tv_usec = MS_TO_USEC(timeout_ms);
    struct sockaddr_in addr;

    int sock = socket(AF_INET, SOCK_DGRAM, IPPROTO_ICMP); // NOLINT(android-cloexec-socket)

    if (sock<0) {
        return SOCKET_ERROR;
    }

    if (setsockopt(sock, SOL_IP, IP_TTL, &ttl, sizeof(ttl))!=0) {
        close(sock);
        return SOCKET_ERROR;
    }

    if (setsockopt(sock, SOL_SOCKET, SO_RCVTIMEO, (const char*)&t_out, sizeof(t_out))!=0) {
        close(sock);
        return SOCKET_ERROR;
    }

    bzero(&addr, sizeof(addr));
    addr.sin_family = AF_INET;
    if (inet_pton(AF_INET, host, &(addr.sin_addr))<0) {
        close(sock);
        return SOCKET_ERROR;
    }

    addr_map[sock] = addr;
    return sock;
}

void close_icmp_socket(int sock) {
    shutdown(sock, SHUT_RDWR);
    close(sock);
    addr_map.erase(sock);
}

#pragma clang diagnostic push
#pragma ide diagnostic ignored "misc-non-private-member-variables-in-classes"
int ping(int sock, u_short sequence, const int size, jbyte* pattern, jsize pattern_len) {
    char packet_data[size+sizeof(icmphdr)];
    struct icmphdr* hdr = (struct icmphdr*)&packet_data;
    struct timespec time_start, time_end;
    struct sockaddr_in addr = addr_map[sock], r_addr;
    socklen_t addr_len;

    bzero(packet_data, sizeof(packet_data));

    hdr->type = ICMP_ECHO;
    hdr->un.echo.id = htons(getpid());
    hdr->un.echo.sequence = htons(sequence);
    if (pattern_len>0) {
        for(int i=0; i<size; i=i+pattern_len) {
            int chunk_size = size - i;
            if (chunk_size>pattern_len)
                chunk_size = pattern_len;
            memcpy(packet_data+i+ sizeof(icmphdr), pattern, (size_t)chunk_size);
        }
    }
    hdr->checksum = checksum((u_short *)&packet_data, sizeof(packet_data));

    clock_gettime(CLOCK_MONOTONIC, &time_start);
    if (sendto(sock, packet_data, sizeof(packet_data), 0, (struct sockaddr*)&addr, sizeof(addr))<0) {
        return SEND_ERROR;
    }

    addr_len = sizeof(r_addr);
    int resp = recvfrom(sock, packet_data, sizeof(packet_data), 0, (struct sockaddr*)&r_addr, &addr_len);
    clock_gettime(CLOCK_MONOTONIC, &time_end);
    if (resp<=0) {
        return SEND_TIMEOUT;
    }
    double elapsed = ((double)(time_end.tv_nsec - time_start.tv_nsec))/1000000.0;
    return (int)((time_end.tv_sec-time_start.tv_sec) * 1000.0+elapsed);

}
#pragma clang diagnostic pop

extern "C" jint Java_me_impa_pinger_Pinger_createicmpsocket(JNIEnv *env, jobject __unused thiz, jstring host, jint timeout, jint ttl) {
    const char *n_host = env->GetStringUTFChars(host, nullptr);
    int result = create_icmp_socket(n_host, timeout, ttl);

    (*env).ReleaseStringUTFChars(host, n_host);

    return result;
}

extern "C" void Java_me_impa_pinger_Pinger_closeicmpsocket(JNIEnv *env, jobject __unused thiz, jint sock) {
    close_icmp_socket(sock);
}

extern "C" jint Java_me_impa_pinger_Pinger_ping(JNIEnv *env, jobject __unused thiz, jint sock, jchar sequence, jint size, jbyteArray pattern) {
    jbyte *const n_pattern =  env->GetByteArrayElements(pattern, nullptr);
    const jsize pattern_len = env->GetArrayLength(pattern);

    jint result = ping(sock, sequence, size, n_pattern, pattern_len);

    (*env).ReleaseByteArrayElements(pattern, n_pattern, JNI_ABORT);

    return result;
}

#pragma clang diagnostic pop