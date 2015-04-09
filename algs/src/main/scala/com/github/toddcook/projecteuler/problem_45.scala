/*
 * Copyright (c) 2011, Todd Cook.
 *   All rights reserved.
 *   Redistribution and use in source and binary forms, with or without modification,
 *   are permitted provided that the following conditions are met:
 *
 *      * Redistributions of source code must retain the above copyright notice,
 *        this list of conditions and the following disclaimer.
 *      * Redistributions in binary form must reproduce the above copyright notice,
 *        this list of conditions and the following disclaimer in the documentation
 *        and/or other materials provided with the distribution.
 *      * Neither the name of the <ORGANIZATION> nor the names of its contributors
 *        may be used to endorse or promote products derived from this software
 *        without specific prior written permission.
 *
 *   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 *   ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *   WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *   DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 *   FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 *   DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 *   SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 *   CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 *   OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 *   OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.github.toddcook.projecteuler

/**
 *  Problem 45
 *  Triangle, pentagonal, and hexagonal numbers are generated by the following
 *  formulae:
 *  Triangle 	  	T_(n)=n(n+1)/2 	  	1, 3, 6, 10, 15, ...
 *  Pentagonal 	  	P_(n)=n(3n-1)/2 	  	1, 5, 12, 22, 35, ...
 *  Hexagonal 	  	H_(n)=n(2n-1) 	  	1, 6, 15, 28, 45, ...
 *
 *  It can be verified that T_(285) = P_(165) = H_(143) = 40755.
 *  Find the next triangle number that is also pentagonal and hexagonal.
 *
 * @author : Todd Cook
 * @since : 5/7/11
 */

object problem_45 {

  def triangleNumber(n: Long) = (n * (n + 1)) / 2

  def pentagonalNumber(n: Long) = (n * (3 * n - 1)) / 2

  def hexagonalNumber(n: Long) = n * (2 * n - 1)

  def isPentagonal(x: Long) = {
    var n = (math.sqrt((24d * x) + 1) + 1) / 6
    (pentagonalNumber(n.longValue) == x)
  }

  def quadraticFormula(a: Long, b: Long, c: Long): Tuple2[Long, Long] = {
    val xLow = (-b + (math.sqrt((b * b) - 4d * a * c))) / (2 * a)
    val xHigh = (-b - (math.sqrt((b * b) - 4d * a * c))) / (2 * a)
    (xLow.longValue, xHigh.longValue)
  }

  def recoverSeeds(x: Long): Tuple3[Long, Long, Long] = {
    val pentSeed = quadraticFormula(3L, -1L, -(2L * x))._1
    val triangleSeed = quadraticFormula(1L, 1L, -(2L * x))._1
    val hexSeed = quadraticFormula(2L, -1L, -x)._1
    (triangleSeed.longValue, pentSeed.longValue, hexSeed.longValue)
  }

  def answerFunctional = {
    //287 + 2
    lazy val candidates: Stream[Long] = Stream.cons(289L, candidates.map(_ + 2))
    val result = candidates.dropWhile(n => {
      ((triangleNumber(n) % 3 != 0) || (!isPentagonal(triangleNumber(n))))
    })
    result(0)
  }

  def answerProcedural() = {
    var ii = 287L
    var finished = false
    while (!finished) {
      if (triangleNumber(ii) % 3 == 0) {
        if (isPentagonal(triangleNumber(ii))) {
          finished = true
        }
      }
      ii += 2 // we know our seed is triangle & heptagonal, and since
      // every other triangular number is heptagonal,
      // via skipping by twos we will match
    }
    (ii - 2)
  }

  /**
   * The nth pentagonal number is one third of the 3n-1th triangular number.
   *  http://en.wikipedia.org/wiki/Pentagonal_number
   *  Every hexagonal number is a triangular number, but only every other
   *  triangular number (the 1st, 3rd, 5th, 7th, etc.) is a hexagonal number.
   *  http://en.wikipedia.org/wiki/Hexagonal_number
   */
  def main(args: Array[String]) = {
    val answerSeed = answerFunctional
    println(answerSeed + " = " + triangleNumber(answerSeed))
    println(answerProcedural)
    println(recoverSeeds(triangleNumber(285)))
    println(recoverSeeds(triangleNumber(55385L)))
  }
}