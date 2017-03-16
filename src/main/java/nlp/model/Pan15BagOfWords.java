package nlp.model;

import model.Language;
import nlp.Pan15SentencePreProcessor;
import org.deeplearning4j.text.sentenceiterator.CollectionSentenceIterator;
import org.deeplearning4j.text.sentenceiterator.SentenceIterator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.util.*;

import static nlp.Utils.getSentencesFromLanguage;
import static nlp.Utils.normalize;

public class Pan15BagOfWords implements Model {

    private static HashMap<Language, LinkedHashMap<String, Integer>> bows = getBOWs();
    private final static int VEC_LENGTH = 5000;
    private boolean binaryBow = true;

    public Pan15BagOfWords(boolean binaryBow) {
        this.binaryBow = binaryBow;
    }


    private static HashMap<Language, LinkedHashMap<String, Integer>> getBOWs() {
        HashMap<Language, LinkedHashMap<String, Integer>> bows = new HashMap<>();
        for (Language language: Language.values()) {
            bows.put(language,getBagOfWordsWithCounts(language));
        }
        return bows;
    }

    private static LinkedHashMap<String, Integer> getBagOfWordsWithCounts(Language language) {
        HashMap<String, Integer> bagOfWords = new HashMap<>();
        List<String> sentences = getSentencesFromLanguage(language);
        SentenceIterator iter = new CollectionSentenceIterator(new Pan15SentencePreProcessor(), sentences);
        while(iter.hasNext()) {
            String sentence = iter.nextSentence();
            for(String word : sentence.split("\\s+")) {
                word =  normalize(word);
                if (Objects.equals(word, "") || (word.length() == 1 && word.matches("\\p{Punct}"))) continue;
                bagOfWords.put(word, bagOfWords.getOrDefault(word, 0) + 1);
            }
        }
        LinkedHashMap<String, Integer> sorted = new LinkedHashMap<>();
        final int[] count = {0};
        bagOfWords.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Collections.reverseOrder())).forEach(
                entry -> {
                    if (count[0] < VEC_LENGTH) sorted.put(entry.getKey(), entry.getValue());
                    count[0]++;
                }
        );
        return sorted;
    }

    public static LinkedList<String> getBagOfWords(Language language) {
        LinkedHashMap<String, Integer> bow = bows.get(language);
        return new LinkedList<>(bow.keySet());
    }

    public INDArray getBinaryBoWVector(String sentence, Language language) {
        LinkedList<String> keys = getBagOfWords(language);
        SentenceIterator iter = new CollectionSentenceIterator(new Pan15SentencePreProcessor(), Collections.singletonList(sentence));
        sentence = iter.nextSentence();
        INDArray featureVector = Nd4j.zeros(1, VEC_LENGTH);
        for(String word : sentence.split("\\s+")) {
            word =  normalize(word);
            int col = keys.indexOf(word);
            if (col > -1) featureVector.putScalar(0, col, 1);
        }
        return featureVector;
    }

    public INDArray getBoWVector(String sentence, Language language) {
        LinkedList<String> keys = getBagOfWords(language);
        SentenceIterator iter = new CollectionSentenceIterator(new Pan15SentencePreProcessor(), Collections.singletonList(sentence));
        sentence = iter.nextSentence();
        INDArray featureVector = Nd4j.zeros(1, VEC_LENGTH);
        for(String word : sentence.split("\\s+")) {
            word =  normalize(word);
            int col = keys.indexOf(word);
            if (col > -1) featureVector.putScalar(0, col, featureVector.getColumn(col).getInt() + 1);
        }
        featureVector.divi(VEC_LENGTH);
        System.out.println(featureVector.getDouble(0,0));
        return featureVector;
    }

    @Override
    public int getVecLength() {
        return VEC_LENGTH;
    }

    @Override
    public INDArray getVector(String sentence, Language language) {
        return (binaryBow) ? getBinaryBoWVector(sentence, language): getBoWVector(sentence, language);
    }
}