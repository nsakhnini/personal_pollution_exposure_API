package com.ninasakhnini;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

//Class to manage communication with AWS Database
public class AWSManager {

    static JSONObject AWSconfiguration = new JSONObject();
    static AmazonDynamoDB client;

    //Function to parse configuration file
    public static void configure(String AWSconfigurationFile) {
        try {
            Path configurationFile = Paths.get(AWSconfigurationFile);
            String bytes = new String(Files.readAllBytes(configurationFile));
            AWSconfiguration = new JSONObject(bytes);
            if (validateAWSConfiguration(AWSconfiguration)) {
                AWSConnect();
            }
        } catch (JSONException jsonException) {
            System.out.println("JSON File Error: " + jsonException.getMessage());
        } catch (IOException ioException) {
            System.out.println("IO Error: " + ioException.getMessage());
        } catch (Exception exception) {
            System.out.println("Error: " + exception.getMessage());
        }
    }

    //Function that creates a client to connect to AWS DynamoDB and attempts to connect
    private static void AWSConnect() {

        BasicAWSCredentials awsCreds = new BasicAWSCredentials((String) AWSconfiguration.get("AccessKeyID"),
                (String) AWSconfiguration.get("SecretKey"));

        try {
            client = AmazonDynamoDBClientBuilder.standard()
                    .withCredentials(new AWSStaticCredentialsProvider(awsCreds))
                    //.withRegion(Regions.US_EAST_1)
                    .withRegion((String) AWSconfiguration.get("Region"))
                    .build();
        } catch (Exception exception) {
            System.out.println("Error: " + exception.getMessage());
        }
    }

    //Function to validate the JSON configuration file
    private static Boolean validateAWSConfiguration(JSONObject configuration) throws Exception {

        Boolean isValid = false;

        if (configuration.has("AccessKeyID") && configuration.has("SecretKey")
                && configuration.has("Region") && configuration.has("Table")) {
            isValid = true;
        } else {
            throw new Exception("JSON File Error. Not in the required format. Please Refer to READ ME.");
        }

        return isValid;
    }

    //========================================================================================
    //========================================================================================

    //Function to write values from DB into Ontology instances
    //TODO: Format to Ontology Instance
    public void DBtoOntologyInstance() {
        try {

            ScanRequest scanRequest = new ScanRequest().withTableName((String) AWSconfiguration.get("Table"));
            ScanResult result = client.scan(scanRequest);

            //To keep the table headers
            HashMap<String, Integer> headersMap = new HashMap();

            //To keep the entities
            List<HashMap<Integer, String>> entities = new ArrayList();

            for (Map<String, AttributeValue> item : result.getItems()) {
                HashMap<Integer, String> row = new HashMap();
                mapHandler.mapHandling("", item, headersMap, row);
                entities.add(row);
            }

            CSVFormat csvFormat = CSVFormat.DEFAULT.withRecordSeparator("\n");
            FileWriter fileWriter;

            try {
                String fileName = (String) AWSconfiguration.get("Table") + ".csv";

                fileWriter = new FileWriter(fileName);
                CSVPrinter csvPrinter = new CSVPrinter(fileWriter, csvFormat);

                //A map for columns using their headers as indexes
                List<Integer> columns = new ArrayList(headersMap.values());
                Collections.sort(columns);
                //A map inverse of Headers
                Map<Integer, String> invertedHeaderMap = new HashMap();

                for (String key : headersMap.keySet()) {
                    invertedHeaderMap.put(headersMap.get(key), key);
                }

                List headersList = new ArrayList();

                for (Integer column : columns) {
                    headersList.add(invertedHeaderMap.get(column));
                }

                csvPrinter.printRecord(headersList);

                //Writing the data in rows
                for (HashMap<Integer, String> row : entities) {
                    List rowList = new ArrayList();

                    for (Integer column : columns) {

                        if (row.containsKey(column)) {

                            if (row.get(column) != null & !row.get(column).isEmpty()) {
                                rowList.add(row.get(column));
                            }
                            else {
                                rowList.add(null);
                            }
                        }
                        else {
                            rowList.add(null);
                        }
                    }
                    csvPrinter.printRecord(rowList);
                }
                csvPrinter.flush();
            }
            catch (IOException ioException) {
                System.out.println("IO Error: " + ioException.getMessage());
            }
        }
        catch (Exception exception) {

            System.out.println("Error: " + exception.getMessage());
        }
    }

    //Function to Query database using a filter expression and a set of attribute names and values
    public ScanResult queryDB(String filterExpression, JSONArray attributeValues, JSONArray attributeNames) {
        ScanRequest scanRequest = new ScanRequest().withTableName((String) AWSconfiguration.get("Table"));

        scanRequest.withFilterExpression(filterExpression);

        Map<String, AttributeValue> attributeValuesMap = new HashMap<String, AttributeValue>();

        for (int i = 0; i < attributeValues.length(); i++) {
            JSONObject value = (JSONObject) attributeValues.get(i);
            String type = value.getString("type");
            AttributeValue av = new AttributeValue();

            switch (type.charAt(0)) {
                case 'N':
                    av.withN(value.getString("value"));
                    break;
                case 'S':
                    av.withS(value.getString("value"));
                    break;
                default:
                    //handle all non numeric as String
                    av.withS(value.getString("value"));
            }
            attributeValuesMap.put(value.getString("name"), av);
        }
        scanRequest.withExpressionAttributeValues(attributeValuesMap);

        Map<String, String> attributeNamesMap = new HashMap<String, String>();

        for (int i = 0; i < attributeNames.length(); i++) {
            JSONObject value = (JSONObject) attributeNames.get(i);
            attributeNamesMap.put(value.getString("name"), value.getString("value"));
        }

        scanRequest.withExpressionAttributeNames(attributeNamesMap);

        ScanResult result = client.scan(scanRequest);
        return result;
    }
}