/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.examples.mllib

import org.apache.log4j.{Level, Logger}
import scopt.OptionParser
import scala.math

import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.mllib.classification.{LogisticRegressionWithLBFGS, SVMWithSGD}
import org.apache.spark.mllib.evaluation.BinaryClassificationMetrics
import org.apache.spark.mllib.util.MLUtils
import org.apache.spark.mllib.optimization.{SquaredL2Updater, L1Updater}
import breeze.stats.distributions.{Gaussian, Uniform}
import breeze.linalg.DenseMatrix

/**
 * An example app for binary classification. Run with
 * {{{
 * bin/run-example org.apache.spark.examples.mllib.BinaryClassification
 * }}}
 * A synthetic dataset is located at `data/mllib/sample_binary_classification_data.txt`.
 * If you use it as a template to create your own app, please use `spark-submit` to submit your app.
 */
object MnistSVMs {

  object Algorithm extends Enumeration {
    type Algorithm = Value
    val SVM, LR = Value
  }

  object RegType extends Enumeration {
    type RegType = Value
    val L1, L2 = Value
  }

  import Algorithm._
  import RegType._

  case class Params(
      input: String = null,
      numIterations: Int = 100,
      stepSize: Double = 1.0,
      algorithm: Algorithm = LR,
      regType: RegType = L2,
      regParam: Double = 0.1)

  def main(args: Array[String]) {
    val defaultParams = Params()

    val parser = new OptionParser[Params]("BinaryClassification") {
      head("BinaryClassification: an example app for binary classification.")
      opt[Int]("numIterations")
        .text("number of iterations")
        .action((x, c) => c.copy(numIterations = x))
      opt[Double]("stepSize")
        .text("initial step size (ignored by logistic regression), " +
          s"default: ${defaultParams.stepSize}")
        .action((x, c) => c.copy(stepSize = x))
      opt[String]("algorithm")
        .text(s"algorithm (${Algorithm.values.mkString(",")}), " +
        s"default: ${defaultParams.algorithm}")
        .action((x, c) => c.copy(algorithm = Algorithm.withName(x)))
      opt[String]("regType")
        .text(s"regularization type (${RegType.values.mkString(",")}), " +
        s"default: ${defaultParams.regType}")
        .action((x, c) => c.copy(regType = RegType.withName(x)))
      opt[Double]("regParam")
        .text(s"regularization parameter, default: ${defaultParams.regParam}")
      arg[String]("<input>")
        .required()
        .text("input paths to labeled examples in LIBSVM format")
        .action((x, c) => c.copy(input = x))
      note(
        """
          |For example, the following command runs this app on a synthetic dataset:
          |
          | bin/spark-submit --class org.apache.spark.examples.mllib.BinaryClassification \
          |  examples/target/scala-*/spark-examples-*.jar \
          |  --algorithm LR --regType L2 --regParam 1.0 \
          |  data/mllib/sample_binary_classification_data.txt
        """.stripMargin)
    }

    parser.parse(args, defaultParams).map { params =>
      run(params)
    } getOrElse {
      sys.exit(1)
    }
  }

  def run(params: Params) {
    val conf = new SparkConf().setAppName(s"BinaryClassification with $params")
    val sc = new SparkContext(conf)

    val parsedData: RDD[(Double, Array[Double])] = sc.textFile(params.input).map( line => {
      val splits = line.split(",").map(_.trim.toDouble)
      val label = splits(0)
      val features = splits.slice(1, splits.size)
      (label, features)
    })

    // val labels = parsedData.collect.keys
    // val featureMatrix = parsedData.values

    val gamma = 0.0004
    val nTrain = 60000
    val nTest = 10000
    val d = 784
    val bigD = 1100

    val gaussianArray = Gaussian(0, 1).sample(bigD*d).toArray
    val wMatrix = (new DenseMatrix(bigD, d, gaussianArray)) * gamma
    val broadcastW = sc.broadcast(wMatrix)
    // val uniformArray = Uniform(0, 1).sample(bigD).toArray
    // val bMatrix = 2*math.Pi*DenseMatrix(bigD, 1, uniformArray)
    // val broadcastB = sc.broadcast(bMatrix)

    def featurizePartition(part: Iterator[(Double, Array[Double])])
      : (DenseMatrix[Double], DenseMatrix[Double]) = {
      val rowMats = part.map({case (label, vals) => new DenseMatrix(1, vals, 0)})
        .toList
      val mat = DenseMatrix.vertcat[Double](rawMats:_*)
      // if (mat.cols != d) { throw new Exception("got matrix size wrong") }
      val features = (mat * broadcastW.value.t)
        .mapValues(v => scala.math.max(v, 0))


      val ones = DenseMatrix.fill(features.rows, 1)(1.0)
      val featuresPlus = DenseMatrix.horzcat(features, ones)
      val labels = DenseMatrix.fill(features.rows, 10)(-1.0)
      var i = 0
      // for ((x, i) <- part.zipWithIndex
      part.foreach({ case (l, _) => {
          labels(i, l) = 1.0
          i += 1
        }
      })
      (labels, featuresPlus)
    }

    val featureLabels = parsedData.mapPartitions(featurizePartition)

    val ftf = featureLabels.map({case(labels, features) => {
      features.t * features
    }.reduce(_ + _)

    val fty = featureLabels.map({case(labels, features) => {
      features.t * labels
    }.reduce(_ + _)

    // linear solve
    val coeffMatrix = ftf \ fty














    // Logger.getRootLogger.setLevel(Level.WARN)
    //
    // val examples = MLUtils.loadLibSVMFile(sc, params.input).cache()
    //
    // val splits = examples.randomSplit(Array(0.8, 0.2))
    // val training = splits(0).cache()
    // val test = splits(1).cache()
    //
    // val numTraining = training.count()
    // val numTest = test.count()
    // println(s"Training: $numTraining, test: $numTest.")
    //
    // examples.unpersist(blocking = false)
    //
    // val updater = params.regType match {
    //   case L1 => new L1Updater()
    //   case L2 => new SquaredL2Updater()
    // }
    //
    // val model = params.algorithm match {
    //   case LR =>
    //     val algorithm = new LogisticRegressionWithLBFGS()
    //     algorithm.optimizer
    //       .setNumIterations(params.numIterations)
    //       .setUpdater(updater)
    //       .setRegParam(params.regParam)
    //     algorithm.run(training).clearThreshold()
    //   case SVM =>
    //     val algorithm = new SVMWithSGD()
    //     algorithm.optimizer
    //       .setNumIterations(params.numIterations)
    //       .setStepSize(params.stepSize)
    //       .setUpdater(updater)
    //       .setRegParam(params.regParam)
    //     algorithm.run(training).clearThreshold()
    // }
    //
    // val prediction = model.predict(test.map(_.features))
    // val predictionAndLabel = prediction.zip(test.map(_.label))
    //
    // val metrics = new BinaryClassificationMetrics(predictionAndLabel)
    //
    // println(s"Test areaUnderPR = ${metrics.areaUnderPR()}.")
    // println(s"Test areaUnderROC = ${metrics.areaUnderROC()}.")
    //
    sc.stop()
  }
}
