package com.krdevops.springai.service.initializr.template;

final class PackageScanBase {

    private PackageScanBase() {
    }

    static String from(String packageName) {
        if (packageName == null || packageName.isBlank()) {
            return "";
        }
        int lastDot = packageName.lastIndexOf('.');
        if (lastDot < 0 || packageName.indexOf('.') == lastDot) {
            return packageName;
        }
        return packageName.substring(0, lastDot);
    }
}
