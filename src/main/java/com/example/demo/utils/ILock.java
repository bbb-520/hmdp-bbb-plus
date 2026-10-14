package com.example.demo.utils;

public interface ILock {
    boolean tryLock(Long timeoutSec);

    void unlock();
}
