package nn.dl4j;

import org.datavec.api.records.reader.RecordReader;
import org.datavec.api.records.reader.impl.csv.CSVRecordReader;
import org.datavec.api.split.FileSplit;
import org.deeplearning4j.earlystopping.EarlyStoppingConfiguration;
import org.deeplearning4j.earlystopping.EarlyStoppingResult;
import org.deeplearning4j.earlystopping.scorecalc.DataSetLossCalculator;
import org.deeplearning4j.earlystopping.termination.MaxEpochsTerminationCondition;
import org.deeplearning4j.earlystopping.termination.MaxTimeIterationTerminationCondition;
import org.deeplearning4j.earlystopping.trainer.EarlyStoppingTrainer;
import org.deeplearning4j.eval.RegressionEvaluation;
import org.deeplearning4j.nn.api.Layer;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.GradientNormalization;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.conf.layers.RBM;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import model.Language;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class DBN  {
    private static Logger log = LoggerFactory.getLogger(DBN.class);
    private MultiLayerNetwork model;
    private int numOutputs = 5;
    private Language language = Language.ENGLISH;
    public File testFile = new File("/Users/mms/Desktop/PR_DNN/dl4j-apr/src/main/resources/supervised/pan15/english" +
            "/english-test-short.csv");
    public File trainFile = new File("/Users/mms/Desktop/PR_DNN/dl4j-apr/src/main/resources/supervised/pan15/english" +
            "/english-train-short.csv");
    static String directory = "/Users/mms/Desktop/PR_DNN/dl4j-apr/src/main/resources/early-stopping/";

    public DBN() { }

    public DBN(Language language, String testFile, String trainFile) {
        this.language = language;
        this.testFile = new File(testFile);
        this.trainFile = new File(trainFile);
    }

    public void train() throws Exception {

        log.info("Load data from " + trainFile.toString() );
        RecordReader recordReader = new CSVRecordReader(1);
        recordReader.initialize(new FileSplit(trainFile));
        DataSetIterator iter = new Pan15DataSetIterator(recordReader,500, 2,6,true, language);

            log.info("Train model....");
            while(iter.hasNext()) {
                DataSet ds = iter.next();
                model.fit( ds ) ;
            }
            log.info("Training done.");
    }


    public String test() throws Exception {

        RecordReader recordReader = new CSVRecordReader(1);
        log.info("Load verification data from " + testFile.toString() ) ;
        recordReader.initialize(new FileSplit(testFile));
        DataSetIterator iter = new Pan15DataSetIterator(recordReader,100, 2,6,true, language);

        RegressionEvaluation eval = new RegressionEvaluation( numOutputs );
        while(iter.hasNext()) {
            DataSet ds = iter.next();
            INDArray predict2 = model.output(ds.getFeatureMatrix(), Layer.TrainingMode.TEST);
            eval.eval(ds.getLabels(), predict2);
        }
        log.info("Testing done");

        return eval.stats() ;
    }

    public static MultiLayerConfiguration getConf(int numInputs) {
        return getModel(numInputs).getLayerWiseConfigurations();
    }

    public static MultiLayerNetwork getModel(int numInputs) {
        int seed = 42;
        MultiLayerConfiguration conf =  new NeuralNetConfiguration.Builder()
                .seed(seed)
                .iterations(300)
                .gradientNormalization(GradientNormalization.ClipElementWiseAbsoluteValue)
                .gradientNormalizationThreshold(1.0)
                .momentum(0.5)
                .momentumAfter(Collections.singletonMap(3, 0.9))
                .regularization(true)
//                .dropOut(0.25)
                .l2(0.02)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .list()
                .layer(0, new RBM.Builder(RBM.HiddenUnit.BINARY, RBM.VisibleUnit.GAUSSIAN)
                        .nIn(numInputs).nOut(2729).updater(Updater.NESTEROVS)
                        .activation("relu").lossFunction(LossFunctions.LossFunction.MSE).build())
                .layer(1, new RBM.Builder(RBM.HiddenUnit.BINARY, RBM.VisibleUnit.GAUSSIAN)
                        .nIn(2729).nOut(2000).updater(Updater.NESTEROVS)
                        .activation("relu").lossFunction(LossFunctions.LossFunction.MSE).build())
                .layer(2, new RBM.Builder(RBM.HiddenUnit.BINARY, RBM.VisibleUnit.GAUSSIAN)
                        .nIn(2000).nOut(1000).updater(Updater.NESTEROVS)
                        .activation("relu").lossFunction(LossFunctions.LossFunction.MSE).build())
                .layer(3, new RBM.Builder(RBM.HiddenUnit.BINARY, RBM.VisibleUnit.GAUSSIAN)
                        .nIn(1000).nOut(500).updater(Updater.NESTEROVS)
                        .activation("relu").lossFunction(LossFunctions.LossFunction.MSE).build())
                .layer(4, new OutputLayer.Builder(LossFunctions.LossFunction.MSE)
                        .updater(Updater.NESTEROVS)
                        .activation("relu")
                        .nIn(500).nOut(5).build())
                .pretrain(true).backprop(true)
                .build();
        return new MultiLayerNetwork(conf);
    }

    public MultiLayerNetwork trainWithEarlyStopping() throws IOException,
            InterruptedException {
        MultiLayerConfiguration myNetworkConfiguration = getConf(250);

        RecordReader recordReader = new CSVRecordReader(1);
        recordReader.initialize(new FileSplit(testFile));
        DataSetIterator myTestData = new Pan15DataSetIterator(recordReader,100, 2,6,true, language);;
        recordReader.initialize(new FileSplit(trainFile));
        DataSetIterator myTrainData = new Pan15DataSetIterator(recordReader,500, 2,6,true, language);;

        EarlyStoppingConfiguration esConf = new EarlyStoppingConfiguration.Builder()
                .epochTerminationConditions(new MaxEpochsTerminationCondition(300))
                .iterationTerminationConditions(new MaxTimeIterationTerminationCondition(20, TimeUnit.MINUTES))
                .scoreCalculator(new DataSetLossCalculator(myTestData, true))
                .evaluateEveryNEpochs(5)
                .build();

        EarlyStoppingTrainer trainer = new EarlyStoppingTrainer(esConf,myNetworkConfiguration,myTrainData);

        //Conduct early stopping training:
        EarlyStoppingResult result = trainer.fit();

        //Print out the results:
        System.out.println("Termination reason: " + result.getTerminationReason());
        System.out.println("Termination details: " + result.getTerminationDetails());
        System.out.println("Total epochs: " + result.getTotalEpochs());
        System.out.println("Best epoch number: " + result.getBestModelEpoch());
        System.out.println("Score at best epoch: " + result.getBestModelScore());

        //Get the best model:
        return (MultiLayerNetwork) result.getBestModel();
    }


    public void runTrainingAndValidate() {
        this.model = getModel(250);
        try {
            this.train();
            System.out.println(this.test());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setModel(MultiLayerNetwork model) {
        this.model = model;
    }
}