package io.pact.core.model.generators

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.pact.core.support.generators.expressions.Adjustment
import io.pact.core.support.generators.expressions.Operation
import io.pact.core.support.generators.expressions.TimeBase
import io.pact.core.support.generators.expressions.TimeExpressionLexer
import io.pact.core.support.generators.expressions.TimeExpressionParser
import io.pact.core.support.generators.expressions.TimeOffsetType
import mu.KLogging
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

data class ParsedTimeExpression(val base: TimeBase, val adjustments: MutableList<Adjustment<TimeOffsetType>>)

object TimeExpression : KLogging() {
  fun executeTimeExpression(base: OffsetDateTime, expression: String?): Result<OffsetDateTime, String> {
    return if (!expression.isNullOrEmpty()) {
      return when (val result = parseTimeExpression(expression)) {
        is Err -> result
        is Ok -> {
          val midnight = OffsetDateTime.of(base.toLocalDate(), LocalTime.MIDNIGHT, ZoneOffset.from(base))
          val noon = OffsetDateTime.of(base.toLocalDate(), LocalTime.NOON, ZoneOffset.from(base))
          var time = when (val valBase = result.value.base) {
            TimeBase.Now -> base
            TimeBase.Midnight -> midnight
            TimeBase.Noon -> noon
            is TimeBase.Am -> midnight.plusHours(valBase.hour.toLong())
            is TimeBase.Pm -> noon.plusHours(valBase.hour.toLong())
            is TimeBase.Next -> if (base.isBefore(noon))
              noon.plusHours(valBase.hour.toLong())
              else midnight.plusHours(valBase.hour.toLong())
          }

          result.value.adjustments.forEach {
            when (it.operation) {
              Operation.PLUS -> {
                time = when (it.type) {
                  TimeOffsetType.HOUR -> time.plusHours(it.value.toLong())
                  TimeOffsetType.MINUTE -> time.plusMinutes(it.value.toLong())
                  TimeOffsetType.SECOND -> time.plusSeconds(it.value.toLong())
                  TimeOffsetType.MILLISECOND -> time.plus(it.value.toLong(), ChronoUnit.MILLIS)
                }
              }
              Operation.MINUS -> {
                time = when (it.type) {
                  TimeOffsetType.HOUR -> time.minusHours(it.value.toLong())
                  TimeOffsetType.MINUTE -> time.minusMinutes(it.value.toLong())
                  TimeOffsetType.SECOND -> time.minusSeconds(it.value.toLong())
                  TimeOffsetType.MILLISECOND -> time.minus(it.value.toLong(), ChronoUnit.MILLIS)
                }
              }
            }
          }

          Ok(time)
        }
      }
    } else {
      Ok(base)
    }
  }

  private fun parseTimeExpression(expression: String): Result<ParsedTimeExpression, String> {
    val charStream = CharStreams.fromString(expression)
    val lexer = TimeExpressionLexer(charStream)
    val tokens = CommonTokenStream(lexer)
    val parser = TimeExpressionParser(tokens)
    val errorListener = ErrorListener()
    parser.addErrorListener(errorListener)
    val result = parser.expression()
    return if (errorListener.errors.isNotEmpty()) {
      Err("Error parsing expression: ${errorListener.errors.joinToString(", ")}")
    } else {
      Ok(ParsedTimeExpression(result.timeBase, result.adj))
    }
  }
}
