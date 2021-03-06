/*
 * Copyright (c) 2010.
 * CC-by Felipe Micaroni Lalli
 */

package br.eti.fml.machinegun.tools;

import br.eti.fml.machinegun.Capsule;
import br.eti.fml.machinegun.WrongCapsuleException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * <p>
 * A fast-food {@link Capsule} implementation using the Java serialization.
 * But come on! Do not use this, you can do better! Use it <u>only
 * if</u> you are really lazy and performance is not your forte.
 * We really recommend you to use the
 * <a href="http://www.google.com.br/search?sourceid=machinegun&q=google+protocol+buffers&btnI=I">Google
 * Protocol Buffers</a>, it is pretty cool and really efficient.
 * <p>
 * <p>
 * It works only if your data type implements {@link java.io.Serializable}.
 * </p>
 *
 * @author Felipe Micaroni Lalli (micaroni@gmail.com)
 *         Nov 20, 2010 4:54:03 PM
 */
public class GenericCapsuleForLazyPeople<T extends Serializable>
        implements Capsule<T> {

    @SuppressWarnings("unchecked")
    @Override
    public T restoreFromBytes(byte[] data) throws WrongCapsuleException {
        try {
            T object;

            ByteArrayInputStream bis = new ByteArrayInputStream(data);
            ObjectInputStream ois = new ObjectInputStream(bis);

            object = (T) ois.readUnshared();
            ois.close();
            bis.close();

            return object;
        } catch (IOException e) {
            throw new WrongCapsuleException("Error on restoreFromBytes(...)", e);
        } catch (ClassNotFoundException e) {
            throw new WrongCapsuleException(
                    "Error on restoreFromBytes(...) Class not found!", e);
        }
    }

    @Override
    public byte[] convertToBytes(T data) throws WrongCapsuleException {
        try {
            byte[] bytes;

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeUnshared(data);
            bytes = baos.toByteArray();

            oos.close();
            baos.close();

            return bytes;
        } catch (IOException e) {
            throw new WrongCapsuleException(
                    "Error on convertToBytes(...): " + data, e);
        }
    }    
}
