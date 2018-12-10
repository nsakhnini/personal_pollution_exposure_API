package com.ninasakhnini;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class mapHandler {

    public static void mapHandling(String path, Map<String, AttributeValue> entity, HashMap<String, Integer> headerMap, HashMap<Integer, String> row)
    {
        for (String key : entity.keySet())
        {
            String keyName = key;
            if (!path.isEmpty())
            {
                keyName = path + "." + key;
            }

            char type = ' ';
            //String
            if (entity.get(key).getS() != null )
            {
                type = 'S';
            }
            //Number
            if (entity.get(key).getN() != null)
            {
                type = 'N';
            }
            //List
            if (entity.get(key).getL() != null)
            {
                type = 'L';
            }
            //Map
            if (entity.get(key).getM() != null )
            {
                type = 'M';
            }
            //Bool
            if (entity.get(key).getBOOL() != null)
            {
                type = 'B';
            }
            //Write to map if primitive type (not list or map)
            if (!(type == 'M' || type =='m') & !(type == 'L' || type =='l') & !headerMap.containsKey(keyName))
            {
                headerMap.put(keyName, headerMap.size());
            }

            switch (type)
            {
                case 'S':
                    row.put(headerMap.get(keyName), entity.get(key).getS());
                    break;
                case 'N':
                    row.put(headerMap.get(keyName), entity.get(key).getN());
                    break;
                case 'B':
                    row.put(headerMap.get(keyName), entity.get(key).getBOOL().toString());
                    break;
                case 'L':
                    Map< String, AttributeValue> listMap = new HashMap();
                    List<AttributeValue> l = entity.get(key).getL();
                    for (AttributeValue v : l)
                    {
                        listMap.put("" + listMap.size(), v);
                    }
                    //Try breaking it down
                    mapHandling(keyName, listMap, headerMap, row);
                    break;
                case 'M':
                    Map< String, AttributeValue> mapSet = entity.get(key).getM();
                    mapHandling(keyName, mapSet, headerMap, row);
                    break;
                default:
                    System.out.println( entity.get(key) + " not written to map.");
            }
        }
    }
}
