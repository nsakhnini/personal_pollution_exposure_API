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

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.FunctionalSyntaxDocumentFormat;
import org.semanticweb.owlapi.io.OWLOntologyDocumentTarget;
import org.semanticweb.owlapi.io.StreamDocumentTarget;
import org.semanticweb.owlapi.model.*;

import javax.annotation.Nonnull;
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

            String ontologyFilePath = "Ontology\\personal_pollution_exposure_ontology.owl";
            String newOntologyPath = "Ontology\\personal_pollution_exposure_ontology_individuals.owl";
            File ontologyFile = new File(ontologyFilePath);
            File newOntologyFile = new File(newOntologyPath);

            String base = "http://nsakhn2.people.uic.edu/Ontology/personal_pollution_exposure";

            OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
            OWLOntology ontology = manager.loadOntologyFromOntologyDocument(ontologyFile);
                    //manager.createOntology(ontologyIRI);
            OWLDataFactory factory = manager.getOWLDataFactory();

            //Create individuals for Computing, Materials, Human Body Parts, Sensor, and Deployment
            //This is done manually because we don't yet have a database for these classes in our project
            //As of now we have only a single device operating
            OWLIndividual owlIndividual = factory.getOWLNamedIndividual(IRI.create(base + "#4_Hours"));
            OWLClass owlClass = factory.getOWLClass(IRI.create(base + "#Battery_Life"));

            OWLClassAssertionAxiom axiom = factory.getOWLClassAssertionAxiom(owlClass, owlIndividual);

            manager.addAxiom(ontology, axiom);
            manager.saveOntology(ontology, IRI.create(newOntologyFile));



                /*OWLObjectProperty hasWife = factory.getOWLObjectProperty(IRI
                        .create(ontologyIRI + "#hasWife"));

                OWLObjectPropertyAssertionAxiom axiom1 = factory
                        .getOWLObjectPropertyAssertionAxiom(hasWife, owlIndividual, mary);

                AddAxiom addAxiom1 = new AddAxiom(ont, axiom1);
                // Now we apply the change using the manager.
                manager.applyChange(addAxiom1);

                System.out.println("RDF/XML: ");*/

            //manager.saveOntology(ont,IRI.create(newOntologyFile));


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
                OWLIndividual pm25 = null;
                OWLIndividual noise = null,windSpeed = null, weather = null, temperature = null, timestamp = null,
                        longitude = null ,latitude = null, microenvironemnt = null, meConfidence = null, individual=null;

                OWLClass latitudeClass = factory.getOWLClass(IRI.create(base + "#geo:latitude"));
                OWLClass longitudeClass = factory.getOWLClass(IRI.create(base + "#geo:longitude"));
                OWLClass microenvironmentClass = factory.getOWLClass(IRI.create(base + "#Microenvironment_Value"));
                OWLClass meConfidenceClass = factory.getOWLClass(IRI.create(base + "#Confidence"));
                OWLClass timestampClass = factory.getOWLClass(IRI.create(base + "#Timestamp"));
                OWLClass temperatureClass = factory.getOWLClass(IRI.create(base + "#AirTemperature"));
                OWLClass weatherClass = factory.getOWLClass(IRI.create(base + "#WeatherPhenomenon"));
                OWLClass windSpeedClass = factory.getOWLClass(IRI.create(base + "#WindSpeed"));
                OWLClass pm25Class = factory.getOWLClass(IRI.create(base + "#PM2.5"));
                OWLClass noiseClass = factory.getOWLClass(IRI.create(base + "#Noise"));

                OWLObjectProperty hasAirTemperature = factory.getOWLObjectProperty(IRI.create(base + "#hasAirTemperature"));
                OWLObjectProperty hasLatitude = factory.getOWLObjectProperty(IRI.create(base + "#hasLatitude"));
                OWLObjectProperty hasLongitude = factory.getOWLObjectProperty(IRI.create(base + "#hasLongitude"));
                OWLObjectProperty hasMEConfidence = factory.getOWLObjectProperty(IRI.create(base + "#hasMEConfidence"));
                OWLObjectProperty hasMicroEnvironment = factory.getOWLObjectProperty(IRI.create(base + "#hasMicroEnvironment"));
                OWLObjectProperty hasNoise = factory.getOWLObjectProperty(IRI.create(base + "#hasNoise"));
                OWLObjectProperty hasPM = factory.getOWLObjectProperty(IRI.create(base + "#hasPM"));
                OWLObjectProperty hasWeatherPhenomenon = factory.getOWLObjectProperty(IRI.create(base + "#hasWeatherPhenomenon"));
                OWLObjectProperty hasWindSpeed = factory.getOWLObjectProperty(IRI.create(base + "#hasWindSpeed"));

                OWLClassAssertionAxiom tempAxiom, latitudeAxiom, longitudeAxiom, meConfidenceAxiom,microenvironmentAxiom,
                        noiseAxiom,timestampAxiom, weatherAxiom, windSpeedAxiom, pmAxiom;

                OWLDifferentIndividualsAxiom differentIndividualsAxiom;

                //Writing the data in rows
                for (HashMap<Integer, String> row : entities) {
                    List rowList = new ArrayList();
                    for (Integer column : columns) {

                        if (row.containsKey(column)) {

                            if (row.get(column) != null & !row.get(column).isEmpty()) {
                                //System.out.println(column.toString() + "  " + headersList.get(column) + "  " + row.get(column));
                                if (headersList.get(column).toString().contains("pm25")) {
                                    individual = factory.getOWLNamedIndividual(IRI.create(base + "#" + row.get(column)));
                                    if(pm25 != null) {
                                        differentIndividualsAxiom = factory.getOWLDifferentIndividualsAxiom(individual, pm25);
                                        manager.addAxiom(ontology, differentIndividualsAxiom);
                                    }
                                    pm25 = individual;
                                    pmAxiom = factory.getOWLClassAssertionAxiom(pm25Class, pm25);
                                    manager.addAxiom(ontology, pmAxiom);
                                    System.out.println(row.get(column));
                                }
                                else if (headersList.get(column).toString().contains("latitude")) {
                                    individual = factory.getOWLNamedIndividual(IRI.create(base + "#" + row.get(column)));
                                    if(latitude != null){
                                        differentIndividualsAxiom = factory.getOWLDifferentIndividualsAxiom(individual,latitude);
                                        manager.addAxiom(ontology,differentIndividualsAxiom);
                                    }
                                    latitude = individual;
                                    latitudeAxiom = factory.getOWLClassAssertionAxiom(latitudeClass, latitude);
                                    manager.addAxiom(ontology, latitudeAxiom);
                                    System.out.println(row.get(column));
                                }
                                else if (headersList.get(column).toString().contains("longitude")) {
                                    individual = factory.getOWLNamedIndividual(IRI.create(base + "#" + row.get(column)));
                                    if (longitude != null) {
                                        differentIndividualsAxiom = factory.getOWLDifferentIndividualsAxiom(individual, longitude);
                                        manager.addAxiom(ontology, differentIndividualsAxiom);
                                    }
                                    longitude = individual;
                                    longitudeAxiom = factory.getOWLClassAssertionAxiom(longitudeClass, longitude);
                                    manager.addAxiom(ontology, longitudeAxiom);
                                    System.out.println(row.get(column));
                                }
                                else if (headersList.get(column).toString().contains("timestamp")) {
                                    String timeString = row.get(column).replace(":", "-").replaceFirst(" ","/")
                                            .replaceFirst(" ", "/").replaceFirst(" ", "time");
                                    individual = factory.getOWLNamedIndividual(IRI.create(base + "#" + timeString));
                                    if(timestamp != null) {
                                        differentIndividualsAxiom = factory.getOWLDifferentIndividualsAxiom(individual, timestamp);
                                        manager.addAxiom(ontology, differentIndividualsAxiom);
                                    }
                                    timestamp = individual;
                                    timestampAxiom = factory.getOWLClassAssertionAxiom(timestampClass, timestamp);
                                    manager.addAxiom(ontology, timestampAxiom);
                                    System.out.println(timeString);
                                }

                                else if (headersList.get(column).toString().contains("dba")) {
                                    individual = factory.getOWLNamedIndividual(IRI.create(base + "#" + row.get(column)));
                                    if(noise != null) {
                                        differentIndividualsAxiom = factory.getOWLDifferentIndividualsAxiom(individual, noise);
                                        manager.addAxiom(ontology, differentIndividualsAxiom);
                                    }
                                    noise = individual;
                                    noiseAxiom = factory.getOWLClassAssertionAxiom(noiseClass, noise);
                                    manager.addAxiom(ontology, noiseAxiom);
                                    System.out.println(row.get(column));
                                }
                                else if (headersList.get(column).toString().contains("weather")) {
                                    individual = factory.getOWLNamedIndividual(IRI.create(base + "#" + row.get(column)));
                                    if(weather != null) {
                                        differentIndividualsAxiom = factory.getOWLDifferentIndividualsAxiom(individual, weather);
                                        manager.addAxiom(ontology, differentIndividualsAxiom);
                                    }
                                    weather = individual;
                                    weatherAxiom = factory.getOWLClassAssertionAxiom(weatherClass, weather);
                                    manager.addAxiom(ontology, weatherAxiom);
                                    System.out.println(row.get(column));
                                }
                                else if (headersList.get(column).toString().contains("temp")) {
                                    individual = factory.getOWLNamedIndividual(IRI.create(base + "#" + row.get(column)));
                                    if(temperature != null) {
                                        differentIndividualsAxiom = factory.getOWLDifferentIndividualsAxiom(individual, temperature);
                                        manager.addAxiom(ontology, differentIndividualsAxiom);
                                    }
                                    temperature = individual;
                                    tempAxiom = factory.getOWLClassAssertionAxiom(temperatureClass, temperature);
                                    manager.addAxiom(ontology, tempAxiom);
                                    System.out.println(row.get(column));
                                }
                                else if (headersList.get(column).toString().contains("wind_speed")) {
                                    individual = factory.getOWLNamedIndividual(IRI.create(base + "#" + row.get(column)));
                                    if(windSpeed != null) {
                                        differentIndividualsAxiom = factory.getOWLDifferentIndividualsAxiom(individual, windSpeed);
                                        manager.addAxiom(ontology, differentIndividualsAxiom);
                                    }
                                    windSpeed = individual;
                                    windSpeedAxiom = factory.getOWLClassAssertionAxiom(windSpeedClass, windSpeed);
                                    manager.addAxiom(ontology, windSpeedAxiom);
                                    System.out.println(row.get(column));
                                }
                                else if (headersList.get(column).toString().contains("menv")) {
                                    individual = factory.getOWLNamedIndividual(IRI.create(base + "#" + row.get(column)));
                                    if(microenvironemnt != null) {
                                        differentIndividualsAxiom = factory.getOWLDifferentIndividualsAxiom(individual, microenvironemnt);
                                        manager.addAxiom(ontology, differentIndividualsAxiom);
                                    }
                                    microenvironemnt = individual;
                                    microenvironmentAxiom = factory.getOWLClassAssertionAxiom(microenvironmentClass, microenvironemnt);
                                    manager.addAxiom(ontology, microenvironmentAxiom);
                                    System.out.println(row.get(column));
                                    //Because Microenvironment up to this point were human-coded
                                    individual = factory.getOWLNamedIndividual(IRI.create(base + "#100.0"));
                                    if(meConfidence != null) {
                                        differentIndividualsAxiom = factory.getOWLDifferentIndividualsAxiom(individual, meConfidence);
                                        manager.addAxiom(ontology, differentIndividualsAxiom);
                                    }
                                    meConfidence = individual;
                                    meConfidenceAxiom = factory.getOWLClassAssertionAxiom(meConfidenceClass, meConfidence);
                                    manager.addAxiom(ontology, meConfidenceAxiom);
                                    System.out.println(row.get(column));
                                }
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

                    OWLPropertyAssertionAxiom propertyAssertionAxiom = factory.getOWLObjectPropertyAssertionAxiom(
                            hasLatitude, pm25, latitude);
                    manager.addAxiom(ontology, propertyAssertionAxiom);

                     propertyAssertionAxiom = factory.getOWLObjectPropertyAssertionAxiom(
                            hasAirTemperature, timestamp, temperature);
                    manager.addAxiom(ontology, propertyAssertionAxiom);


                    propertyAssertionAxiom = factory.getOWLObjectPropertyAssertionAxiom(
                            hasLongitude, timestamp, longitude);
                    manager.addAxiom(ontology, propertyAssertionAxiom);

                    propertyAssertionAxiom = factory.getOWLObjectPropertyAssertionAxiom(
                            hasMEConfidence, timestamp, meConfidence);
                    manager.addAxiom(ontology, propertyAssertionAxiom);

                    propertyAssertionAxiom = factory.getOWLObjectPropertyAssertionAxiom(
                            hasMicroEnvironment, timestamp, microenvironemnt);
                    manager.addAxiom(ontology, propertyAssertionAxiom);

                    propertyAssertionAxiom = factory.getOWLObjectPropertyAssertionAxiom(
                            hasNoise, timestamp, noise);
                    manager.addAxiom(ontology, propertyAssertionAxiom);

                    propertyAssertionAxiom = factory.getOWLObjectPropertyAssertionAxiom(
                            hasPM, timestamp, pm25);
                    manager.addAxiom(ontology, propertyAssertionAxiom);

                    propertyAssertionAxiom = factory.getOWLObjectPropertyAssertionAxiom(
                            hasWeatherPhenomenon, timestamp, weather);
                    manager.addAxiom(ontology, propertyAssertionAxiom);

                    propertyAssertionAxiom = factory.getOWLObjectPropertyAssertionAxiom(
                            hasWindSpeed, timestamp, windSpeed);
                    manager.addAxiom(ontology, propertyAssertionAxiom);

                    //csvPrinter.printRecord(rowList);
                }
                //csvPrinter.flush();
            }
            catch (IOException ioException) {
                System.out.println("IO Error: " + ioException.getMessage());
            }


            manager.saveOntology(ontology, IRI.create(newOntologyFile));
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

//==================================
            /*OWLIndividual owlIndividual2 = factory.getOWLNamedIndividual(IRI.create(base + "#1,4GHz 64-bit quad-core Broadcom Arm Cortex A53-architecture"));
            OWLClass owlClass2 = factory.getOWLClass(IRI.create(base + "#Processor"));

            OWLClassAssertionAxiom axiom2 = factory.getOWLClassAssertionAxiom(owlClass2, owlIndividual2);

            manager.addAxiom(ontology, axiom2);
            manager.saveOntology(ontology, IRI.create(newOntologyFile));*/
//==================================
            /*owlIndividual = factory.getOWLNamedIndividual(IRI.create(base + "#512MB LPDDR2 SDRAM"));
            owlClass = factory.getOWLClass(IRI.create(base + "#RAM"));

            axiom = factory.getOWLClassAssertionAxiom(owlClass, owlIndividual);

            manager.addAxiom(ontology, axiom);
            manager.saveOntology(ontology, IRI.create(newOntologyFile));*/
//==================================
            /*owlIndividual = factory.getOWLNamedIndividual(IRI.create(base + "#Waist"));
            owlClass = factory.getOWLClass(IRI.create(base + "#Human_Body_Parts"));

            axiom = factory.getOWLClassAssertionAxiom(owlClass, owlIndividual);

            manager.addAxiom(ontology, axiom);
            manager.saveOntology(ontology, IRI.create(newOntologyFile));
            //==================================
            owlIndividual = factory.getOWLNamedIndividual(IRI.create(base + "#Plastic"));
            owlClass = factory.getOWLClass(IRI.create(base + "#Materials"));

                    axiom = factory.getOWLClassAssertionAxiom(owlClass, owlIndividual);

            manager.addAxiom(ontology, axiom);
            manager.saveOntology(ontology, IRI.create(newOntologyFile));
            //==================================
            /*owlIndividual = factory.getOWLNamedIndividual(IRI.create(base + "#Plantower PMS7003"));
            owlClass = factory.getOWLClass(IRI.create(base + "#Sensor"));

            axiom = factory.getOWLClassAssertionAxiom(owlClass, owlIndividual);

            manager.addAxiom(ontology, axiom);
            manager.saveOntology(ontology, IRI.create(newOntologyFile));
            //==================================
            owlIndividual = factory.getOWLNamedIndividual(IRI.create(base + "#80%"));//Our Inference
            owlClass = factory.getOWLClass(IRI.create(base + "#Sensor_Accuracy"));

            axiom = factory.getOWLClassAssertionAxiom(owlClass, owlIndividual);

            manager.addAxiom(ontology, axiom);
            manager.saveOntology(ontology, IRI.create(newOntologyFile));
            //==================================
            owlIndividual = factory.getOWLNamedIndividual(IRI.create(base + "#https://www.google.com/url?sa=t&rct=j&q=&esrc=s&source=web&cd=1&ved=2ahUKEwixzsnfyqDfAhUI0IMKHa5QCEIQFjAAegQIBhAC&url=https%3A%2F%2Fbotland.com.pl%2Findex.php%3Fcontroller%3Dattachment%26id_attachment%3D2182&usg=AOvVaw2Z0uuD73nnU8hqrJq6zAvM"));
            owlClass = factory.getOWLClass(IRI.create(base + "#Sensor_Datasheet"));

            axiom = factory.getOWLClassAssertionAxiom(owlClass, owlIndividual);

            manager.addAxiom(ontology, axiom);
            manager.saveOntology(ontology, IRI.create(newOntologyFile));
            //==================================
            owlIndividual = factory.getOWLNamedIndividual(IRI.create(base + "#12 reading per minute"));
            owlClass = factory.getOWLClass(IRI.create(base + "#Sensor_Rate"));

            axiom = factory.getOWLClassAssertionAxiom(owlClass, owlIndividual);

            manager.addAxiom(ontology, axiom);
            manager.saveOntology(ontology, IRI.create(newOntologyFile));
            //==================================
            owlIndividual = factory.getOWLNamedIndividual(IRI.create(base + "#PM2.5 Sensor"));
            owlClass = factory.getOWLClass(IRI.create(base + "#Type"));

            axiom = factory.getOWLClassAssertionAxiom(owlClass, owlIndividual);

            manager.addAxiom(ontology, axiom);
            manager.saveOntology(ontology, IRI.create(newOntologyFile));
            //==================================
            owlIndividual = factory.getOWLNamedIndividual(IRI.create(base + "#UIC Deployment"));
            owlClass = factory.getOWLClass(IRI.create(base + "#ssnDeployment"));

            axiom = factory.getOWLClassAssertionAxiom(owlClass, owlIndividual);

            manager.addAxiom(ontology, axiom);
            manager.saveOntology(ontology, IRI.create(newOntologyFile));
            //==================================
            owlIndividual = factory.getOWLNamedIndividual(IRI.create(base + "#myCityMeter"));
            owlClass = factory.getOWLClass(IRI.create(base + "#Wearable"));

            axiom = factory.getOWLClassAssertionAxiom(owlClass, owlIndividual);

            manager.addAxiom(ontology, axiom);
            manager.saveOntology(ontology, IRI.create(newOntologyFile));
            //==================================
            owlIndividual = factory.getOWLNamedIndividual(IRI.create(base + "#myCityMeter components"));
            //OWLIndividual owlOtherIndividual = factory.getOWLNamedIndividual(IRI.create(base + "#myCityMeter capabilities"));
            owlClass = factory.getOWLClass(IRI.create(base + "#Computing_Capabilites"));

            axiom = factory.getOWLClassAssertionAxiom(owlClass, owlIndividual);

            manager.addAxiom(ontology, axiom);
            manager.saveOntology(ontology, IRI.create(newOntologyFile));*/