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
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.FSDirectory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
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

            BufferedReader in = null;
            in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

            QueryParser parser = new QueryParser("Body", analyzer);
            while (true) {
                System.out.println("Enter query: ");

                String queryString = in.readLine();

                if (queryString == null || queryString.length() == -1) {
                    break;
                }

                queryString = queryString.trim();
                if (queryString.length() == 0) {
                    break;
                }

                Map<String, Integer> negTerms = getExpansionTerms(queryString, 200, 220, analyzer, similarity, null);

                List<Map.Entry<String, Integer>> top3Negative = getTopTerms(negTerms);

                Map<String, Integer> posTerms = getExpansionTerms(queryString, 0, 3, analyzer, similarity, top3Negative);

                List<Map.Entry<String, Integer>> top3Positive = getTopTerms(posTerms);

                // Implement the query expansion by selecting terms from the expansionTerms
                String first = "";
                String second = "";
                String third = "";
                if(top3Positive.size() == 3){
                    first = top3Positive.get(0).getKey();
                    second = top3Positive.get(1).getKey();
                    third = top3Positive.get(2).getKey();
                }

                queryString = queryString + " " + first + " " + second + " " + third; //Expanded query

                System.out.println(queryString);

                if (queryString.equals("")) {
                    break;
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


    public Map<String, Integer>  getExpansionTerms(String queryString, int startDoc, int numExpDocs, Analyzer analyzer, Similarity similarity, List<Map.Entry<String, Integer>> top3Negative) {

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

            System.out.println(" baseDoc + numExpDocs = "+(startDoc + numExpDocs));
            System.out.println(" hits.length = "+hits.length);

            long numTotalHits = results.totalHits;
            System.out.println(numTotalHits + " total matching documents");

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
                        if(top3Negative == null || (!top3Negative.get(0).getKey().equals(term) &&
                                !top3Negative.get(1).getKey().equals(term) &&
                                !top3Negative.get(2).getKey().equals(term))) {
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
        System.out.println(topTerms.size());
        return topTerms;
    }

    private List<Map.Entry<String, Integer>> getTopTerms(Map<String, Integer> terms) {
        List<Map.Entry<String, Integer>> top3Entries = new ArrayList();

        for (Map.Entry<String, Integer> term: terms.entrySet()) {
            // This is the minimum frequency
            if (term.getValue() >= 1) {
                if(top3Entries.size() < 3){
                    top3Entries.add(term);
                } else {
                    if(term.getValue() >= top3Entries.get(0).getValue()){
                        top3Entries.set(2, top3Entries.get(1));
                        top3Entries.set(1, top3Entries.get(0));
                        top3Entries.set(0, term);
                    } else if(term.getValue() >= top3Entries.get(1).getValue()){
                        top3Entries.set(2, top3Entries.get(1));
                        top3Entries.set(1, term);
                    } else if(term.getValue() >= top3Entries.get(2).getValue()){
                        top3Entries.set(2, term);
                    }
                }

                System.out.println(term.getKey() + " -> " + term.getValue() + " times");
            }
        }
        return top3Entries;
    }


    public static void main(String[] args) {

        Lab5_QueryExpansion baseline = new Lab5_QueryExpansion();

        Analyzer analyzer = new StandardAnalyzer();
        Similarity similarity = new ClassicSimilarity();

        // Search the index
        baseline.indexSearchQE(analyzer, similarity);
    }

}
