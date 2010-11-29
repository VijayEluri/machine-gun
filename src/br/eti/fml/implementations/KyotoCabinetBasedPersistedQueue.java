/*
 * Copyright (c) 2010.
 * CC-by Felipe Micaroni Lalli
 */

package br.eti.fml.implementations;

import br.eti.fml.machinegun.auditorship.ArmyAudit;
import br.eti.fml.machinegun.externaltools.Consumer;
import br.eti.fml.machinegun.externaltools.PersistedQueueManager;
import com.google.protobuf.InvalidProtocolBufferException;
import kyotocabinet.DB;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

public class KyotoCabinetBasedPersistedQueue implements PersistedQueueManager {
    private Map<String, DB> db = new HashMap<String, DB>();   
    private ArrayList<Thread> threads = new ArrayList<Thread>();
    private long size = 0;
    private boolean closed = false;
    private Random random = new Random();

    public KyotoCabinetBasedPersistedQueue(File directory, String ... queues) {
        directory.mkdirs();

        if (!directory.isDirectory()) {
            throw new IllegalArgumentException(directory + " must be a directory!");
        }

        for (String queue : queues) {
            DB db = new DB();
            this.db.put(queue, db);

            if (!db.open(directory.getAbsolutePath()
                    + File.separatorChar + "queue-"
                    + queue.hashCode() + ".kch", DB.OWRITER | DB.OCREATE)) {

                throw db.error();
            }

            db.tune_encoding("UTF-8");

            db.cas(head(queue), null, longToBytes(0L));
            db.cas(tail(queue), null, longToBytes(-1L));
        }
    }

    private byte[] head(String queue) {
        return stringToBytes(queue + ".HEAD");        
    }

    private byte[] tail(String queue) {
        return stringToBytes(queue + ".TAIL");        
    }

    private byte[] stringToBytes(String s) {
        try {
            return s.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] longToBytes(Long value) {
        if (value == null) {
            return null;
        } else {
            Primitives.LongType.Builder data
                    = Primitives.LongType.newBuilder();
            
            data.setValue(value);
            Primitives.LongType bytes = data.build();
            return bytes.toByteArray();            
        }
    }

    public Long bytesToLong(byte[] bytes) {
        if (bytes == null) {
            return null;
        } else {
            try {
                Primitives.LongType data
                        = Primitives.LongType.parseFrom(bytes);
    
                return data.getValue();
            } catch (InvalidProtocolBufferException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void putIntoAnEmbeddedQueue(
            ArmyAudit armyAudit, String queueName, byte[] data)
                throws InterruptedException {

        DB db = this.db.get(queueName);

        if (db.begin_transaction(true)) {
            boolean ok = false;

            while (!ok) {
                long tail = bytesToLong(db.get(tail(queueName)));
                ok = db.cas(tail(queueName), longToBytes(tail), longToBytes(tail + 1));

                if (ok) {
                    db.set(stringToBytes("addr." + tail), data);
                }
            }

            db.end_transaction(true);
        }
    }

    @Override
    public void registerANewConsumerInAnEmbeddedQueue(
            final ArmyAudit armyAudit, final String queueName,
            final Consumer consumer) {

        size++;

        final DB db = KyotoCabinetBasedPersistedQueue.this.db.get(queueName);        

        AtomicReference<Thread> t = new AtomicReference<Thread>(
                new Thread("consumer " + size + " of " + size) {

            public void run() {
                while (!closed) {
                    try {
                        if (db.begin_transaction(true)) {
                            long headToBeProcessed = bytesToLong(
                                    db.get(head(queueName)));

                            long tail = bytesToLong(db.get(tail(queueName)));
                            boolean needProcess = false;

                            if (tail >= headToBeProcessed) {
                                needProcess = db.cas(head(queueName),
                                        longToBytes(headToBeProcessed),
                                        longToBytes(headToBeProcessed + 1L));
                            }

                            db.end_transaction(true);

                            if (!needProcess) {
                               Thread.sleep(random.nextInt(100) + 10);
                            } else {
                                byte[] data = db.get(stringToBytes("addr."
                                        + headToBeProcessed));

                                consumer.consume(data);
                            }
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                armyAudit.consumerHasBeenStopped(Thread.currentThread().getName());
            }
        });

        threads.add(t.get());
        t.get().start();
    }
/*
    public byte[] booleanToBytes(Boolean value) {
        if (value == null) {
            return null;
        } else {
            Primitives.BooleanType.Builder data
                    = Primitives.BooleanType.newBuilder();

            data.setValue(value);
            Primitives.BooleanType bytes = data.build();
            return bytes.toByteArray();
        }
    }

    public Boolean bytesToBoolean(byte[] bytes) {
        if (bytes == null) {
            return null;
        } else {
            try {
                Primitives.BooleanType data
                        = Primitives.BooleanType.parseFrom(bytes);

                return data.getValue();
            } catch (InvalidProtocolBufferException e) {
                throw new RuntimeException(e);
            }
        }
    }
*/
    public synchronized void close() throws InterruptedException {
        if (!closed) {
            closed = true;

            for (Thread t : threads) {
                t.join();
            }

            for (DB db : this.db.values()) {
                if (!db.close()) {
                    db.error().printStackTrace();
                }
            }
        }
    }

    public void finalize() {
        try {
            close();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public boolean isEmpty(String queueName) {
        return bytesToLong(this.db.get(queueName).get(head(queueName)))
                > bytesToLong(this.db.get(queueName).get(tail(queueName)));
    }

    @Override
    public void killAllConsumers(String queueName) throws InterruptedException {
        close();
    }
}
