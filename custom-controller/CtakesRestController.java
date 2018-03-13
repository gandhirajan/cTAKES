/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ctakes.rest.service;

import opennlp.tools.coref.mention.Mention;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.ctakes.core.pipeline.PipelineBuilder;
import org.apache.ctakes.core.pipeline.PiperFileReader;
import org.apache.ctakes.core.util.textspan.DefaultTextSpan;
import org.apache.ctakes.core.util.textspan.TextSpan;
import org.apache.ctakes.relationextractor.pipelines.RelationExtractorConsumer;
import org.apache.ctakes.rest.util.RelationExtractorHelper;
import org.apache.ctakes.rest.util.XMLParser;
import org.apache.ctakes.typesystem.type.relation.TemporalTextRelation;
import org.apache.ctakes.typesystem.type.textspan.Sentence;
import org.apache.log4j.Logger;
import org.apache.uima.UIMAFramework;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.cas.impl.XmiCasSerializer;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.util.JCasPool;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import javax.servlet.ServletException;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;


/*
 * Rest web service that takes clinical text
 * as input and produces extracted text as output
 */
@RestController
public class CtakesRestController {

    private static final Logger LOGGER = Logger.getLogger(CtakesRestController.class);
    private static final String DEFAULT_PIPER_FILE_PATH = "pipers/Default.piper";
    private static final String FULL_PIPER_FILE_PATH = "pipers/Full.piper";
    private static final String DEFAULT_PIPELINE = "Default";
    private static final String FULL_PIPELINE = "Full";
    private static final Map<String, PipelineRunner> _pipelineRunners = new HashMap<>();
    public enum MentionNames {
        DiseaseDisorderMention, SignSymptomMention, ProcedureMention,
        AnatomicalSiteMention, MedicationMention, LabMention
    }

    @PostConstruct
    public void init() throws ServletException {
        LOGGER.info("Initializing analysis engines and jcas pools");
        _pipelineRunners.put(DEFAULT_PIPELINE, new PipelineRunner(DEFAULT_PIPER_FILE_PATH));
        _pipelineRunners.put(FULL_PIPELINE, new PipelineRunner(FULL_PIPER_FILE_PATH));
    }

    @RequestMapping(value = "/analyze", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, Map<String, List<String>>> getAnalyzedJSON(@RequestBody String analysisText,
                                                                  @RequestParam("pipeline") Optional<String> pipelineOptParam)
            throws Exception {
        String pipeline = DEFAULT_PIPELINE;
        if(pipelineOptParam.isPresent()) {
            if(FULL_PIPELINE.equalsIgnoreCase(pipelineOptParam.get())) {
                pipeline = FULL_PIPELINE;
            }
        }
        final PipelineRunner runner = _pipelineRunners.get(pipeline);
        return runner.process(analysisText);

    }

    static private Map<String, Map<String, List<String>>> formatResults(JCas jcas) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        XmiCasSerializer.serialize(jcas.getCas(), output);
        String outputStr = output.toString();
        Files.write(Paths.get("Result.xml"), outputStr.getBytes());
        XMLParser parser = new XMLParser();
        return parser.parse(new ByteArrayInputStream(outputStr.getBytes()));
    }

    static private final class PipelineRunner {
        private final AnalysisEngine _engine;
        private final JCasPool _pool;

        private PipelineRunner(final String piperPath) throws ServletException {
            try {
                PiperFileReader reader = new PiperFileReader(piperPath);
                PipelineBuilder builder = reader.getBuilder();
                AnalysisEngineDescription pipeline = builder.getAnalysisEngineDesc();
                _engine = UIMAFramework.produceAnalysisEngine(pipeline);
                _pool = new JCasPool(10, _engine);
            } catch (Exception e) {
                LOGGER.error("Error loading pipers");
                throw new ServletException(e);
            }
        }

        public Map<String, Map<String, List<String>>> process(final String text) throws ServletException {
            JCas jcas = null;
            Map<String, Map<String, List<String>>> resultMap = null;
            if (text != null) {
                try {
                    jcas = _pool.getJCas(-1);
                    jcas.setDocumentText(text);
                    _engine.process(jcas);
                    resultMap = formatResults(jcas);
                    String tlinkStr = getTlinks(jcas);
                    String[] tlinkArray = tlinkStr.split(",");
                    List<String> tlinkList = new ArrayList<>();
                    if(tlinkStr != null) {
                        Map<String,List<String>> tlinkMap = new HashMap<>();
                        tlinkList.add(tlinkStr);
                        //tlinkMap.put("TLINKS:", tlinkList);
                        //resultMap.put("TlinkDetails", tlinkMap);
                    }
                    for (MentionNames mentionName: MentionNames.values()) {
                        tlinkExtractor(resultMap, tlinkArray, mentionName.name());
                    }

                    /*RelationExtractorHelper consumer = new RelationExtractorHelper();
                    String relationDetails = consumer.process(jcas);
                    if(relationDetails != null) {
                        Map<String,List<String>> relationMap = new HashMap<>();
                        List<String> relationList = new ArrayList<>();
                        relationList.add(relationDetails);
                        relationMap.put("RELATIONS:", relationList);
                        resultMap.put("RelationDetails", relationMap);
                    }*/
                    _pool.releaseJCas(jcas);
                } catch (Exception e) {
                    LOGGER.error("Error processing Analysis engine");
                    throw new ServletException(e);
                }
            }
            return resultMap;
        }


        private String getTlinks(JCas jcas) throws Exception {
            final StringBuilder sb = new StringBuilder();
            Collection<Sentence> sentences = JCasUtil.select(jcas,
                    Sentence.class);
            for (Sentence sentence : sentences) {
                final Collection<TemporalTextRelation> tlinks = JCasUtil.select( jcas, TemporalTextRelation.class );
                if ( tlinks == null || tlinks.isEmpty() ) {
                    return null;
                }
                final Collection<TemporalTextRelation> sentenceTlinks = new ArrayList<>();
                final TextSpan sentenceTextSpan = new DefaultTextSpan( sentence.getBegin(), sentence.getEnd() );
                for ( TemporalTextRelation tlink : tlinks ) {
                    final Annotation argument1 = tlink.getArg1().getArgument();
                    final TextSpan argument1Span = new DefaultTextSpan( argument1, 0 );
                    if ( sentenceTextSpan.overlaps( argument1Span ) ) {
                        sentenceTlinks.add( tlink );
                    }
                }
                if ( sentenceTlinks.isEmpty() ) {
                    return null;
                }

                for ( TemporalTextRelation tlink : sentenceTlinks ) {
                    sb.append( tlink.getArg1().getArgument().getCoveredText() ).append( " " );
                    sb.append( tlink.getCategory() ).append( " " );
                    sb.append( tlink.getArg2().getArgument().getCoveredText() ).append( " , " );
                }
                sb.setLength( sb.length() - 3 );
            }
            return sb.toString();
        }
    }

    private static void tlinkExtractor(Map<String, Map<String, List<String>>> resultMap, String[] tlinkArray, String mentionName) {
        Map<String,List<String>>  mentionMap = resultMap.get(mentionName);
        if(mentionMap == null) {
            return;
        }
        Iterator itr = mentionMap.entrySet().iterator();
        while (itr.hasNext()) {
            List<String> mentionValList = null;
            Map.Entry entry = (Map.Entry) itr.next();
            String mentionValue = (String)entry.getKey();
            for(String tlinkString : tlinkArray) {
                if(tlinkString != null) {
                    String matcher = mentionValue + " CONTAINS";
                    String tlinkValue = "";
                    tlinkString = tlinkString.toUpperCase();
                    matcher = matcher.toUpperCase();
                    if(tlinkString.contains(matcher)) {
                        int startPos = tlinkString.indexOf(matcher);
                        /*int endPos = tlinkString.indexOf(",",startPos);
                        if(endPos == -1) {
                            endPos = tlinkString.length();
                        }*/
                        tlinkValue = tlinkString.substring(startPos + matcher.length());
                    }
                    mentionValList = (List) entry.getValue();
                    if(!"".equals(tlinkValue)) {
                        mentionValList.add("tlink: " + tlinkValue);
                    }
                }
            }
            if(mentionValList != null) {
                mentionMap.put(mentionValue,mentionValList);
            }
        }
        resultMap.put(mentionName,mentionMap);
    }
}
