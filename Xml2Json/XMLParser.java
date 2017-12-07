package org.apache.ctakes.rest.util;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;


public class XMLParser {  

    public Map<String,List<String>> parse(InputStream in) throws Exception {
        XMLInputFactory inputFactory = XMLInputFactory.newInstance();
        XMLStreamReader streamReader = inputFactory.createXMLStreamReader(in);
        String analysisText = null;
        List<String> disorderList = new ArrayList<>();
        List<String> findingsList = new ArrayList<>();
        List<String> procedureList = new ArrayList<>();
        List<String> timeList = new ArrayList<>();
        List<String> fractionStrengthList = new ArrayList<>();
        List<String> drugChangeStatusList = new ArrayList<>();
        List<String> strengthUnitList = new ArrayList<>();
        List<String> strengthList = new ArrayList<>();
        List<String> routeList = new ArrayList<>();
        List<String> frequencyUnitList = new ArrayList<>();
        List<String> measurementList = new ArrayList<>();
        List<String> dateList = new ArrayList<>();
        List<String> companyNameList = new ArrayList<>();


        Map<String, String> disorderPosMap = new HashMap<>();
        Map<String, String> findingsPosMap = new HashMap<>();
        Map<String, String> procedurePosMap = new HashMap<>();
        Map<String, String> timePosMap = new HashMap<>();
        Map<String, String> fractionStrengthPosMap = new HashMap<>();
        Map<String, String> drugChangeStatusPosMap = new HashMap<>();
        Map<String, String> strengthUnitPosMap = new HashMap<>();
        Map<String, String> strengthPosMap = new HashMap<>();
        Map<String, String> routePosMap = new HashMap<>();
        Map<String, String> frequencyUnitPosMap = new HashMap<>();
        Map<String, String> measurementPosMap = new HashMap<>();
        Map<String, String> datePosMap = new HashMap<>();
        Map<String, String> companyPosMap = new HashMap<>();

        while (streamReader.hasNext()) {
            if (streamReader.isStartElement()) {
                if(analysisText == null) {
                    if (streamReader.getLocalName().equalsIgnoreCase("Sofa")) {
                        analysisText = streamReader.getAttributeValue(4);
                    }
                }
                try {
                    disorderList = extractData(streamReader, analysisText, SemanticNames.DiseaseDisorderMention.name(), disorderList, disorderPosMap);
                    findingsList = extractData(streamReader, analysisText, SemanticNames.SignSymptomMention.name(), findingsList, findingsPosMap);
                    procedureList = extractData(streamReader, analysisText, SemanticNames.ProcedureMention.name(), procedureList, procedurePosMap);
                    timeList = extractData(streamReader, analysisText, SemanticNames.TimeMention.name(), timeList, timePosMap);
                    fractionStrengthList = extractData(streamReader, analysisText, SemanticNames.FractionStrengthAnnotation.name(), fractionStrengthList, fractionStrengthPosMap);
                    drugChangeStatusList = extractData(streamReader, analysisText, SemanticNames.DrugChangeStatusAnnotation.name(), drugChangeStatusList, drugChangeStatusPosMap);
                    strengthUnitList = extractData(streamReader, analysisText, SemanticNames.StrengthUnitAnnotation.name(), strengthUnitList, strengthUnitPosMap);
                    strengthList = extractData(streamReader, analysisText, SemanticNames.StrengthAnnotation.name(), strengthList, strengthPosMap);
                    routeList = extractData(streamReader, analysisText, SemanticNames.RouteAnnotation.name(), routeList, routePosMap);
                    frequencyUnitList = extractData(streamReader, analysisText, SemanticNames.FrequencyUnitAnnotation.name(), frequencyUnitList, frequencyUnitPosMap);
                    dateList = extractData(streamReader, analysisText, SemanticNames.DateAnnotation.name(), dateList, datePosMap);
                    measurementList = extractData(streamReader, analysisText, SemanticNames.MeasurementAnnotation.name(), measurementList, measurementPosMap);
                    companyNameList = extractData(streamReader, analysisText,SemanticNames.CompanyAnnotation.name(), companyNameList, companyPosMap);
                }
                catch(Exception e) {
                    e.printStackTrace();
                }

            }
            streamReader.next();
        }

        Map<String,List<String>> responseMap = new HashMap<String,List<String>>();
        responseMap.put(SemanticNames.DiseaseDisorderMention.name(),disorderList);
        responseMap.put(SemanticNames.SignSymptomMention.name(),findingsList);
        responseMap.put(SemanticNames.ProcedureMention.name(),procedureList);
        responseMap.put(SemanticNames.TimeMention.name(),timeList);
        responseMap.put(SemanticNames.FractionStrengthAnnotation.name(),fractionStrengthList);
        responseMap.put(SemanticNames.DrugChangeStatusAnnotation.name(),drugChangeStatusList);
        responseMap.put(SemanticNames.StrengthUnitAnnotation.name(),strengthUnitList);
        responseMap.put(SemanticNames.StrengthAnnotation.name(),strengthList);
        responseMap.put(SemanticNames.RouteAnnotation.name(),routeList);
        responseMap.put(SemanticNames.FrequencyUnitAnnotation.name(),frequencyUnitList);
        responseMap.put(SemanticNames.DateAnnotation.name(),dateList);
        responseMap.put(SemanticNames.MeasurementAnnotation.name(),measurementList);
        responseMap.put(SemanticNames.CompanyAnnotation.name(),companyNameList);
        return responseMap;
    }

    private List<String> extractData(XMLStreamReader streamReader, String analysisText, String mentionName, List<String> semanticList, Map<String, String> semanticPosMap) {
        if (streamReader.getLocalName().equalsIgnoreCase(mentionName)) {
            Integer start = Integer.parseInt(streamReader.getAttributeValue(2));
            Integer end = Integer.parseInt(streamReader.getAttributeValue(3));
            String chunk = analysisText.substring(start, end);
            String chunkUpper = chunk.toUpperCase();
            boolean isFound = false;
            if (!semanticList.contains(chunkUpper)) {
                String chunkArray[] = chunk.split("\\s");
                for (String chunkedString : chunkArray) {
                    final String trimmedChunkString = chunkedString.trim();
                    if (!trimmedChunkString.equals("")) {
                        Object[] matchedSemanticArray = semanticList.stream().filter(str -> str.trim().contains(trimmedChunkString.toUpperCase())).toArray();
                        if (matchedSemanticArray.length > 0) {
                            for (Object semanticObj : matchedSemanticArray) {
                                String semanticTerm = semanticObj.toString();
                                String pos = semanticPosMap.get(semanticTerm);
                                String posArr[] = pos.split(",");
                                int startPos = Integer.parseInt(posArr[0]);
                                int endPos = Integer.parseInt(posArr[1]);
                                if (start >= startPos && end <= endPos) {
                                    isFound = true;
                                    break;
                                }
                                if (start <= startPos && end == endPos) {
                                    semanticList.remove(semanticTerm);
                                    semanticList.add(chunkUpper);
                                    semanticPosMap.remove(semanticTerm);
                                    semanticPosMap.put(chunkUpper, start + "," + end);
                                    isFound = true;
                                    break;
                                }
                            }
                        }
                    }
                }
                if (!isFound) {
                    semanticList.add(chunkUpper);
                    System.out.println("mentionName -> " + mentionName +" part -> "+ chunkUpper + " -- " + start + "," + end);
                    semanticPosMap.put(chunkUpper, start + "," + end);
                }

            }
        }
        return semanticList;
    }

    public enum SemanticNames {
        DiseaseDisorderMention, SignSymptomMention, ProcedureMention, TimeMention,
        FractionStrengthAnnotation, DrugChangeStatusAnnotation, StrengthUnitAnnotation,
        StrengthAnnotation, RouteAnnotation, FrequencyUnitAnnotation, DateAnnotation,
        MeasurementAnnotation, CompanyAnnotation
    }

    public static void main(String args[]) throws Exception {
        InputStream stream = new FileInputStream("D:\\Gandhi\\github\\cTAKES\\Xml2Json\\Output.xml");
        XMLParser parser = new XMLParser();
        Map<String,List<String>> outputMap = parser.parse(stream);
        for (Map.Entry<String,List<String>> entry : outputMap.entrySet())
        {
            System.out.println(entry.getKey() + " --> " + entry.getValue().toString());
        }
    }
}
