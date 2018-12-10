package com.ninasakhnini;

public class Main {

    static AWSManager awsManager;
    public static void main(String[] args) {

        awsManager = new AWSManager();
        String AWSconfigurationFile = "raw/awsconfiguration.json";
        if (args.length >= 2)
        {
            AWSconfigurationFile = args[1];
        }
        awsManager.configure(AWSconfigurationFile);
        awsManager.DBtoOntologyInstance();
    }
}
