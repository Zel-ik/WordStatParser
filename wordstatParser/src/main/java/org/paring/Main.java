package org.paring;


import org.paring.config.AccessConfigs;
import org.paring.service.RequestService;

public class Main {
    public static void main(String[] args) {
        AccessConfigs accessConfigs = new AccessConfigs();
        accessConfigs.init();

        RequestService requestService = new RequestService(accessConfigs);
        requestService.sendRequestToWordStat();
    }
}