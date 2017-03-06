import model.Language;
import nlp.Pan15BagOfWords;
import nn.dl4j.DBN;

public class English {
    public static void main(String... args) {
        DBN dbn = new DBN(
                Language.ENGLISH,
                new Pan15BagOfWords(),
                "/Users/mms/Desktop/PR_DNN/dl4j-apr/src/main/resources/supervised/pan15/english/english-test" +
                        "-pan15.csv",
                "/Users/mms/Desktop/PR_DNN/dl4j-apr/src/main/resources/supervised/pan15/english/english-train" +
                        "-pan15.csv"
        );
        dbn.runTrainingAndValidate();
    }
}