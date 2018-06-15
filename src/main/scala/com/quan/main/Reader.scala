package com.quan.main

import com.quan.context.Context
import org.apache.spark.rdd.RDD

object Reader {
  def read(fileName: String, delimiter: String): RDD[Array[Double]] = {
    val sc = Context.getSparkContext;
    val textFile = sc.textFile(fileName)
    val r = textFile.map((lines) => {
      lines.trim().split(delimiter).map((str) => str.toDouble)
    })
    r
  }
}
