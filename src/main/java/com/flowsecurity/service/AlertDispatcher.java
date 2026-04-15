package com.flowsecurity.service;

import com.flowsecurity.model.Alert;

public interface AlertDispatcher {

    void dispatch(Alert alert);
}
