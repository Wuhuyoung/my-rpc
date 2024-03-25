package com.han.rpc.registry;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.KeyValue;
import io.etcd.jetcd.kv.GetResponse;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class EtcdTest {
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        Client client = Client.builder().endpoints("http://localhost:2379").build();
        KV kvClient = client.getKVClient();
        ByteSequence key = ByteSequence.from("test_key".getBytes());
        ByteSequence value = ByteSequence.from("test_value".getBytes());

// put the key-value
        kvClient.put(key, value).get();

// get the CompletableFuture
        CompletableFuture<GetResponse> getFuture = kvClient.get(key);

// get the value from CompletableFuture
        GetResponse response = getFuture.get();
        List<KeyValue> keyValues = response.getKvs();
        for (KeyValue keyValue : keyValues) {
            System.out.println("key:" + keyValue.getKey().toString());
            System.out.println("value:" + keyValue.getValue().toString());
        }

// delete the key
        kvClient.delete(key).get();
    }
}
