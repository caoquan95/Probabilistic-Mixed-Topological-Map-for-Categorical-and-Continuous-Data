package com.quan.model

import breeze.linalg._
import com.quan.util.DistributionHelper
import org.apache.spark.rdd.RDD

class ContinuousModel(val numRows: Int, val numCols: Int) extends Serializable {
  def logPXOverC(
                  contData: RDD[(Long, Vector[Double])],
                  cells: Array[Array[Cell]]):
  RDD[(Long, Array[Array[Double]])] = {
    println("Cont Model: pXOverC")
    contData.mapValues(x => {
      val temp = for (row <- 0 until numRows)
        yield (
          for (col <- 0 until numCols)
            yield DistributionHelper.normalMultivariateLog(x, cells(row)(col).contMean, cells(row)(col).contVariance)
          ).toArray
      temp.toArray
    })
  }

  /**
    * compute the mean for continuous data w(c)
    *
    * @param logPCOverX
    * @param contData
    * @return
    */
  def mean(logPCOverX: RDD[(Long, Array[Double])],
           contData: RDD[(Long, Vector[Double])]): Array[Array[Vector[Double]]] = {
    println("Cont Model: mean")

    val pCOverX = logPCOverX.mapValues((v: Array[Double]) => {
      for (row <- 0 until numRows) {
        for (col <- 0 until numCols) {
          val index: Int = DistributionHelper.index(row, col, numCols)
          v(index) = scala.math.exp(v(index))
        }
      }
      v
    })

    val denominator: Array[Double] = pCOverX.map(_._2)
      .reduce((v1: Array[Double], v2: Array[Double]) => {
        for (row <- 0 until numRows) {
          for (col <- 0 until numCols) {
            val index: Int = DistributionHelper.index(row, col, numCols)
            v1(index) += v2(index)
          }
        }
        v1
      })

    var numerator: Array[Array[Vector[Double]]] = contData.join(pCOverX).mapValues(v => {
      val x: Vector[Double] = v._1
      val p: Array[Double] = v._2
      val temp = for (row <- 0 until numRows)
        yield (
          for (col <- 0 until numCols)
            yield x * p(DistributionHelper.index(row, col, numCols)) // x * p(c / x)
          ).toArray
      temp.toArray
    }).map(_._2).reduce((v1: Array[Array[Vector[Double]]], v2: Array[Array[Vector[Double]]]) => {
      for (row <- 0 until numRows) {
        for (col <- 0 until numCols) {
          v1(row)(col) += v2(row)(col)
        }
      }
      v1
    })

    val t = for (row <- 0 until numRows)
      yield (
        for (col <- 0 until numCols)
          yield numerator(row)(col) / denominator(DistributionHelper.index(row, col, numCols))
        ).toArray
    val res = t.toArray
    res
  }

  /**
    * Compute the Standard deviation for continuous variable
    *
    * @param logPCOverX
    * @param contData
    * @param contMean
    * @return
    */
  def variance(logPCOverX: RDD[(Long, Array[Double])],
               contData: RDD[(Long, Vector[Double])],
               contMean: Array[Array[Vector[Double]]],
               contSize: Int
              ): Array[Array[Double]] = {
    println("Cont Model: Variance")
    val pCOverX: RDD[(Long, Array[Double])] = logPCOverX.mapValues((v: Array[Double]) => {
      for (row <- 0 until numRows) {
        for (col <- 0 until numCols) {
          val index = DistributionHelper.index(row, col, numCols)
          v(index) = scala.math.exp(v(index))
        }
      }
      v
    })

    val denumerator: Array[Double] = pCOverX.map(_._2)
      .reduce((v1: Array[Double], v2: Array[Double]) => {
        for (row <- 0 until numRows) {
          for (col <- 0 until numCols) {
            val index = DistributionHelper.index(row, col, numCols)
            v1(index) += v2(index)
          }
        }
        v1
      }).map(_ * contSize)

    var numerator: Array[Array[Double]] = contData.join(pCOverX)
      .mapValues((v: (Vector[Double], Array[Double])) => {
        val x: Vector[Double] = v._1
        val p: Array[Double] = v._2
        val temp = for (row <- 0 until numRows)
          yield (
            for (col <- 0 until numCols)
              yield scala.math.pow(norm(contMean(row)(col) - x), 2) * p(DistributionHelper.index(row, col, numCols)) // x * p(c / x)
            ).toArray
        temp.toArray
      }).map(_._2).reduce((v1: Array[Array[Double]], v2: Array[Array[Double]]) => {
      for (row <- 0 until numRows) {
        for (col <- 0 until numCols) {
          v1(row)(col) += v2(row)(col)
        }
      }
      v1
    })

    val t = for (row <- 0 until numRows)
      yield (
        for (col <- 0 until numCols)
          yield numerator(row)(col) /
            denumerator(DistributionHelper.index(row, col, numCols))
        ).toArray
    t.toArray
  }
}
