'''This module offers network (socket) services, used for auto detection of an MQTT
broker.

.. module:: ip_utilities
   :synopsis: Implements MQTT broker auto detection and host ip/name retrieval
.. moduleauthor:: Rene van Ee <rene@sandbax.org>

'''

import socket
from socket import AF_INET, SOCK_STREAM
import queue
import threading
from typing import Tuple, List, Set


class IPutilities(queue.Queue):
    def retrieve_host_name_ips(self) -> Tuple[str, Set[str]]:
        host_name: str = None
        host_ips: Set[str] = set()
        host_name = socket.gethostname()
        if host_name is not None:
            addrInfo = socket.getaddrinfo(host_name, 0, family=AF_INET)
            host_ips = {a[-1][0] for a in addrInfo}
        return host_name, host_ips

    def scandomain(self, ip_list: List[str], enable_autodetection: bool, port: int) -> str:
        if enable_autodetection:
            # Add all IP address in the current 'domain' to ip_list. This domain is h.h.h.x/8,
            # where h.h.h.h is host ip and x = [0x00 - 0xFF]
            _, host_ips = self.retrieve_host_name_ips()
            if host_ips is not None:
                for host_ip in host_ips:
                    ip4 = [int(i) for i in host_ip.split('.')]
                    ip_domain = f"{ip4[0]}.{ip4[1]}.{ip4[2]}"
                    for i in range(0x01, 0xFF): # exclude 0x00 and 0xFF
                        ip = f"{ip_domain}.{i}"
                        ip_list.append(ip)

        # Create and start a separate thread for each ip in ip_list
        threadlist = []
        for ip in ip_list:
            t = threading.Thread(target=self._testip, daemon=True, args=(ip, port,))
            t.start()
            threadlist.append(t)
        # If one of the IP's is a 'hit', it will send a message to the queue within
        # seconds. If not, we will have to wait for all threads to finish, maybe
        # the user just started a server and it may still be able to connect.
        # The number of seconds to wait here is arbitrary, it should just not be too
        # large (< 20s).
        try:
            mqtt_ip = self.get(True, 5)  # Somewhat arbitrary number of seconds to wait
            return mqtt_ip
        except:
            print(f"IPutilities: no broker/server detected yet, still waiting")

        # No IP found yet. Now wait for all threads to finish
        [t.join() for t in threadlist]

        # Retrieve IP from queue, if one was posted
        if self.empty():
            return None
        mqtt_ip = self.get()
        return mqtt_ip

    def _testip(self, ip: str, port: int):
        s = socket.socket(AF_INET, SOCK_STREAM)
        conn = s.connect_ex((ip, port))
        if conn == 0:
            self.put(ip)
        s.close()
