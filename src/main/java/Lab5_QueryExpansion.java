import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.FSDirectory;

import java.io.*;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.sql.SQLOutput;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

public class Lab5_QueryExpansion extends Lab1_Baseline {

    IndexSearcher searcher = null;

    public void indexSearchQE(Analyzer analyzer, Similarity similarity) {

        IndexReader reader = null;
        try {
            reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPath)));
            searcher = new IndexSearcher(reader);
            searcher.setSimilarity(similarity);

            QueryParser parser = new QueryParser("Body", analyzer);

            try (BufferedReader br = new BufferedReader(new FileReader("./eval/queries.offline.txt"))) {
                String queryString = br.readLine();
                String simString = similarity.toString().replace("(", "_").replace(")", "_").replace(" ", "_").replace("=", "").replaceAll(",", "_");
                String filename = "./eval/" + simString + ".txt";
                File resultFile = new File(filename);

                resultFile.delete();
                resultFile.createNewFile();
                int counter = 0;

                while (queryString != null) {
                    int questionID = Integer.parseInt(queryString.substring(0, queryString.indexOf(":")));

                    queryString = queryString.trim();
                    queryString = queryString.substring(queryString.indexOf(":") + 1);
                    if (queryString.length() == 0) {
                        break;
                    }

                    int numberOfTerms = 3; //Exercise 2
                    int numberOfDocs = 10; //Exercise 1

                    //Exercise 4
                    Map<String, Integer> negTerms = getExpansionTerms(queryString, 230, 250, analyzer, similarity, null);

                    List<Map.Entry<String, Integer>> topNegative = getTopTerms(negTerms, numberOfTerms); //Get the negative terms

                    Map<String, Integer> posTerms = getExpansionTerms(queryString, 0, numberOfDocs, analyzer, similarity, topNegative);

                    List<Map.Entry<String, Integer>> topTermsAux = getTopTerms(posTerms, numberOfTerms);
                    List<String> topTerms = topTermsAux.stream().map(Map.Entry::getKey).collect(Collectors.toList()); //Convert key, value to just key
                    if (!topTerms.isEmpty())
                        topTerms.set(0, " " + topTerms.get(0));

                    //Exercise 4
                    queryString = removeNegativeTerms(queryString, topNegative.stream().map(Map.Entry::getKey).collect(Collectors.toList()));

//                   Exercise 3
                    topTerms = weightQuery(queryString, topTerms, 0.5); //Get a new list of keys that are weighted

                    //Expanding the query
                    queryString = expandQuery(queryString, topTerms);
                    Query query;

                    //SECOND PASSAGE THROUGH THE INDEX
                    try {
                        query = parser.parse(queryString); //Parsing the expanded query
                    } catch (org.apache.lucene.queryparser.classic.ParseException e) {
                        System.out.println("Error parsing query string.");
                        continue;
                    }


                    TopDocs results = searcher.search(query, 100);
                    ScoreDoc[] hits = results.scoreDocs;

                    try (FileWriter fw = new FileWriter(resultFile, true);
                         BufferedWriter bw = new BufferedWriter(fw);
                         PrintWriter out = new PrintWriter(bw)) {
                        if (counter == 0)
                            out.println("QueryID\t\t\tQ0\t\t\tDocID\t\t\tRank\t\t\tScore\t\t\tRunID");
                        for (int j = 0; j < hits.length; j++) {
                            Document doc = searcher.doc(hits[j].doc);
                            int AnswerId = doc.getField("AnswerId").numericValue().intValue();
                            out.println(questionID + "\t\t\tQ0\t\t\t" + AnswerId + "\t\t\t" + (j + 1) + "\t\t\t" + hits[j].score + "\t\t\trun-1");
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    if (queryString.equals("")) {
                        break;
                    }
                    counter++;
                    queryString = br.readLine();
                }
            }
            reader.close();
        } catch (IOException e) {
            try {
                reader.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            e.printStackTrace();
        }
    }


    public Map<String, Integer> getExpansionTerms(String queryString, int startDoc, int numExpDocs, Analyzer analyzer, Similarity similarity, List<Map.Entry<String, Integer>> topNegatives) {

        Map<String, Integer> topTerms = new HashMap<String, Integer>();

        try {
            QueryParser parser = new QueryParser("Body", analyzer);
            Query query;
            try {
                query = parser.parse(queryString);
            } catch (org.apache.lucene.queryparser.classic.ParseException e) {
                System.out.println("Error parsing query string.");
                return null;
            }

            TopDocs results = searcher.search(query, startDoc + numExpDocs);

            ScoreDoc[] hits = results.scoreDocs;

            for (int j = startDoc; j < hits.length; j++) {
                Document doc = searcher.doc(hits[j].doc);
                String answer = doc.get("Body");
                Integer AnswerId = doc.getField("AnswerId").numericValue().intValue();

                TokenStream stream = analyzer.tokenStream("field", new StringReader(answer));
                // get the CharTermAttribute from the TokenStream
                CharTermAttribute termAtt = stream.addAttribute(CharTermAttribute.class);

                try {
                    stream.reset();

                    // print all tokens until stream is exhausted
                    while (stream.incrementToken()) {
                        String term = termAtt.toString();
                        Integer termCount = topTerms.get(term);
                        //Filter terms that are in the negative feedback documents
                        if (!term.equals("p") && (topNegatives == null ||
                                !topNegatives.stream().map(Map.Entry::getKey).collect(Collectors.toList()).contains(term))) {
                            if (termCount == null)
                                topTerms.put(term, 1);
                            else
                                topTerms.put(term, ++termCount);
                        }
                    }

                    stream.end();
                } finally {
                    stream.close();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return topTerms;
    }

    private List<Map.Entry<String, Integer>> getTopTerms(Map<String, Integer> terms, int numberOfTerms) {
        return terms
                .entrySet()
                .stream()
                .filter(entry -> !entry.getKey().startsWith("http://"))
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(numberOfTerms)
                .collect(Collectors.toList());
    }

    private String expandQuery(String query, List<String> topTerms) {
        return query + String.join(" ", topTerms);
    }

    private List weightQuery(String query, List<String> topTerms, double expansionPercentage) {
        int numberOfExpansionTerms = topTerms.size();
        int numberOfOriginalTerms = query
                .replace("?", " ")
                .replace(".", " ")
                .replace("\"\"", "")
                .replace("(", " ")
                .replace(")", " ")
                .replace("  ", " ").split(" ").length;

        double expansionWeight = ((double) Math.round(((expansionPercentage * numberOfOriginalTerms) / (numberOfExpansionTerms * (1 - expansionPercentage))) * 100)) / 100.0;
        List<String> listToReturn = topTerms.stream().map(term -> term + "^" + expansionWeight).collect(Collectors.toList());
        return listToReturn;
    }

    private String removeNegativeTerms(String query, List<String> negativeTerms) {

        for (String negativeTerm : negativeTerms) {
            if (!query.isEmpty())
                query = query.replaceAll(negativeTerm, "");
        }

        return query.replaceAll(" +", " ").replace("?", "");
    }

    public static void main(String[] args) {


        Lab2_Analyser analyzer = new Lab2_Analyser();

        Similarity similarity = new LMJelinekMercerSimilarity(0.9f); //WE used this value because it has the best p10 and MAP
        Lab5_QueryExpansion baseline = new Lab5_QueryExpansion();
        baseline.indexSearchQE(analyzer, similarity);
    }

}