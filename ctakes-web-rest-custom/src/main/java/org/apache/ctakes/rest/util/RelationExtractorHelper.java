package org.apache.ctakes.rest.util;
import java.util.List;

import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.util.JCasUtil;

import org.apache.ctakes.typesystem.type.relation.BinaryTextRelation;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.ctakes.typesystem.type.textspan.Sentence;

public class RelationExtractorHelper {
    // TODO: turn these into configuration parameters
    public final boolean displayEntities = false;
    public final boolean displayContext = false;

    public String process(JCas jCas) throws AnalysisEngineProcessException {

        JCas systemView;
        StringBuilder sb = new StringBuilder();
        try {
            systemView = jCas.getView(CAS.NAME_DEFAULT_SOFA);
        } catch (CASException e) {
            throw new AnalysisEngineProcessException(e);
        }

        if (displayEntities) {
            sb.append("\n");
            for (IdentifiedAnnotation identifiedAnnotation : JCasUtil.select(systemView, IdentifiedAnnotation.class)) {
                String text = identifiedAnnotation.getCoveredText();
                int type = identifiedAnnotation.getTypeID();
                sb.append(text);
                sb.append("/");
                sb.append(type);
                //System.out.format("%s/%d\n", text, type);
            }
            sb.append("\n");
        }

        // print relations
        sb.append("\n");
        for (BinaryTextRelation binaryTextRelation : JCasUtil.select(systemView, BinaryTextRelation.class)) {

            String category = binaryTextRelation.getCategory();

            IdentifiedAnnotation entity1; // entity whose role is "Argument"
            IdentifiedAnnotation entity2; // entity whose role is "Related_to"

            if (binaryTextRelation.getArg1().getRole().equals("Argument")) {
                entity1 = (IdentifiedAnnotation) binaryTextRelation.getArg1().getArgument();
                entity2 = (IdentifiedAnnotation) binaryTextRelation.getArg2().getArgument();
            } else {
                entity1 = (IdentifiedAnnotation) binaryTextRelation.getArg2().getArgument();
                entity2 = (IdentifiedAnnotation) binaryTextRelation.getArg1().getArgument();
            }

            String arg1 = entity1.getCoveredText();
            String arg2 = entity2.getCoveredText();

            int type1 = entity1.getTypeID();
            int type2 = entity2.getTypeID();

            // print relation and its arguments: location_of(colon/6, colon cancer/2)
            sb.append(category).append("(").append(arg1).append("/").append(type1);
            sb.append(", ").append(arg2).append("/").append(type2).append(")").append("\n");
            //System.out.format("%s(%s/%d, %s/%d)\n", category, arg1, type1, arg2, type2);

            if (displayContext) {
                List<Sentence> list = JCasUtil.selectCovering(jCas, Sentence.class, entity1.getBegin(), entity1.getEnd());

                // print the sentence containing this instance
                for (Sentence s : list) {
                    sb.append(s.getCoveredText());
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

}
