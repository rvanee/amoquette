#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# ---------------------------------------------------------------------------
# Created By  : Rene F. van Ee
# Created Date: 30-Dec-2021
# version ='1.0'
# ---------------------------------------------------------------------------
# Simple script measuring roundtrip delay for 'heartbeat messages' to and
# from a MQTT broker
# ---------------------------------------------------------------------------
# Imports
# ---------------------------------------------------------------------------
import paho.mqtt.client as mqtt
import time
from statistics import mean, stdev
from ip_utilities import IPutilities

port = 1883
duration = 60   # [s]
frequency = 10  # per second

heartbeat_topic = "testbroker/heartbeat"
dt = []


def on_message(client, userdata, message):
    if message.topic == heartbeat_topic:
        global dt
        deltat = 1000.0 * (time.time() - float(message.payload.decode("utf-8")))
        dt.append(deltat)
        print(f'Roundtrip delay: {deltat:.1f} ms')


def on_connect(client, userdata, flags, rc):
    if rc == 0:
        client.connected_flag = True  # set flag
        print("Connected with return code = ", rc)
        # client.subscribe(topic)
    else:
        print("Bad connection with return code = ", rc)


def on_disconnect(client, userdata, rc):
    print("Disconnected with reason " + str(rc))
    client.connected_flag = False


if __name__ == "__main__":
    iputilities = IPutilities()
    host_name, host_ips = iputilities.retrieve_host_name_ips()
    client_id = ""
    if host_ips is not None:
        client_id = f"testclient_{'_'.join([str(i) for i in host_ips])}"
    else:
        print("Can't retrieve host ip, possibly problem with socket library.")
        exit()

    # Try to find a broker at the 'domains' in the host_ips list
    mqttbroker_ip = iputilities.scandomain(list(host_ips), True, port)
    if mqttbroker_ip is not None:
        print(f"MQTT broker/server found at {mqttbroker_ip}:{port}. Trying to connect now...")
    else:
        print("MQTT broker/server not found! Please start broker/server")
        exit()

    # Now try to connect
    client = mqtt.Client(client_id)
    client.connected_flag = False
    client.on_message = on_message
    client.on_connect = on_connect
    client.on_disconnect = on_disconnect
    client.connect(mqttbroker_ip, port)  # connect to broker
    client.loop_start()  # start the loop

    while not client.connected_flag:
        time.sleep(1)

    # Perform subscriptions
    client.subscribe(heartbeat_topic)

    # Send messages
    for i in range(frequency * duration):
        current_time = time.time()
        client.publish(heartbeat_topic, str(current_time))
        time.sleep(1/frequency)

    client.disconnect()
    while client.connected_flag:
        time.sleep(1)
    client.loop_stop()  # stop the loop

    print("\n===============")
    print("Roundtrip delay")
    print(f"Min = {min(dt):.1f} ms")
    print(f"Max = {max(dt):.1f} ms")
    print(f"Mean = {mean(dt):.1f} ms")
    print(f"Std = {stdev(dt):.1f} ms")
