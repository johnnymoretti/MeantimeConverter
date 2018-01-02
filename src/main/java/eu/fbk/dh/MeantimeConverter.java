package eu.fbk.dh;

import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.nio.file.Paths;
import java.util.*;

/**
 * Created by giovannimoretti on 02/12/17.
 */
public class MeantimeConverter {
    public static void main(String[] args) {

        try {
            java.nio.file.Files.walk(Paths.get(args[0])).parallel()
                    .filter(p -> p.toString().endsWith(".xml"))
                    .forEach(filePath -> {
                        try {
                            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
                            XPathFactory xPathfactory = XPathFactory.newInstance();
                            XPath xpath = xPathfactory.newXPath();

                            InputStream stream = new FileInputStream(filePath.toFile());

                            Document doc = dBuilder.parse(stream);
                            doc.getDocumentElement().normalize();

                            XPathExpression expr;
                            NodeList tokens, markables, relations, targets, sources;


                            expr = xpath.compile("/Document");

                            Map<Integer, Integer> refersToMultimap = new HashMap<>();

                            Node doc_node = (Node) expr.evaluate(doc, XPathConstants.NODE);
                            Element elem = (Element) doc_node;

                            String fname = elem.getAttribute("doc_name");
                            System.out.println(fname);
                            expr = xpath.compile("/Document//token");
                            tokens = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);

                            expr = xpath.compile("/Document/Relations/REFERS_TO");
                            relations = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);

                            Map<Integer, String> target_type_map = new HashMap<>();

                            for (int i = 0; i < relations.getLength(); i++) {

                                Node referesNode = relations.item(i);
                                System.out.println(((Element) referesNode).getAttribute("r_id"));


                                expr = xpath.compile("./target");
                                targets = (NodeList) expr.evaluate(referesNode, XPathConstants.NODESET);
                                Integer target = Integer.parseInt(((Element) targets.item(0)).getAttribute("m_id"));
                                target_type_map.put(target, "");


                                expr = xpath.compile("./source");
                                sources = (NodeList) expr.evaluate(referesNode, XPathConstants.NODESET);

                                for (int x = 0; x < sources.getLength(); x++) {
                                    refersToMultimap.put(Integer.parseInt(((Element) sources.item(x)).getAttribute("m_id")), target);

                                }


                            }
                            expr = xpath.compile("/Document/Markables/*");
                            markables = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);

                            SortedSet<String> markableTags = new TreeSet<>();

                            for (int i = 0; i < markables.getLength(); i++) {
                                Node fileNode = markables.item(i);
                                String tagname = ((Element) fileNode).getTagName();
                                markableTags.add(tagname);
                                Integer m_id = Integer.parseInt(((Element) fileNode).getAttribute("m_id"));
                                if (target_type_map.keySet().contains(m_id))
                                {
                                    String ent_type = ((Element) fileNode).getAttribute("ent_type");
                                    String classe = ((Element) fileNode).getAttribute("class");

                                    if (ent_type.length() == 0){
                                        target_type_map.put(m_id,classe);
                                        markableTags.add(tagname+":"+classe);
                                    }else{
                                        target_type_map.put(m_id,ent_type);
                                        markableTags.add(tagname+":"+ent_type);
                                    }
                                }

                                if (tagname.equals("TIMEX3") || tagname.equals("VALUE")){
                                    String type = ((Element) fileNode).getAttribute("type");
                                    markableTags.add(tagname +":"+ type);
                                }

                            }

                            System.out.println(target_type_map);

                            Map<String, String> annotated_tokens = new HashMap<>();


                            for (int i = 0; i < markables.getLength(); i++) {
                                Node fileNode = markables.item(i);
                                String tagname = ((Element) fileNode).getTagName();

                                Integer m_id = Integer.parseInt(((Element) fileNode).getAttribute("m_id"));
                                if (refersToMultimap.containsKey(m_id)){
                                    tagname =  tagname + ":" + target_type_map.get(refersToMultimap.get(m_id));
                                    markableTags.add(tagname);
                                }

                                if (tagname.equals("TIMEX3") || tagname.equals("VALUE")){
                                    String type = ((Element) fileNode).getAttribute("type");
                                    tagname =  tagname + ":" +type;
                                }

                                expr = xpath.compile("./token_anchor");
                                NodeList token_anchor = (NodeList) expr.evaluate(fileNode, XPathConstants.NODESET);

                                for (int anchor_id = 0; anchor_id < token_anchor.getLength(); anchor_id++) {

                                    String ref_id = ((Element) token_anchor.item(anchor_id)).getAttribute("t_id");
                                    annotated_tokens.put(tagname + ref_id, anchor_id == 0 ? "B-" + tagname : "I-" + tagname);
                                }

                            }




                            StringBuffer sb = new StringBuffer();
                            //   System.out.println(annotated_tokens);
                            Integer feat_num = markableTags.size();
                            int current_sentence = 0;

                            ArrayList<String> blacklist = new ArrayList<>();
                           // blacklist.add("LOCATION");
                           // blacklist.add("TIMEX3");

                            for (int i = 0; i < tokens.getLength(); i++) {
                                Node fileNode = tokens.item(i);
                                Element tok_elem = ((Element) fileNode);
                                Integer sentence_number = Integer.parseInt(tok_elem.getAttribute("sentence"));

                                LinkedList<String> tok = new LinkedList<>();
                                tok.add(tok_elem.getTextContent());
                                tok.add("O"); // add for custom version
                                for (String s : markableTags) {

                                    if (blacklist.contains(s)) { //add for custom
                                        tok.set(1, "O"); //add for custom
                                        continue; //add for custom
                                    } //add for custom

                                    tok.set(1, annotated_tokens.containsKey(s + tok_elem.getAttribute("t_id")) ? annotated_tokens.get(s + tok_elem.getAttribute("t_id")) : "O");
                                    if (annotated_tokens.containsKey(s + tok_elem.getAttribute("t_id"))) { // add for custom
                                        break; // add for custom
                                    } // add for custom
                                }

                                if (current_sentence != sentence_number) {
                                    current_sentence = sentence_number;
                                    sb.append("\n");
                                }
                                sb.append(Joiner.on("\t").skipNulls().join(tok) + "\n");

                            }

                            Writer out = new BufferedWriter(new OutputStreamWriter(
                                    new FileOutputStream(args[0] + "/" + fname+".txt"), "UTF-8"));
                            try {
                                out.write(sb.toString());
                            } finally {
                                out.close();
                            }

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
