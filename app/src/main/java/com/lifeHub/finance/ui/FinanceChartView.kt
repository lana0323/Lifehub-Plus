package com.lifeHub.finance.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.lifeHub.R
import com.lifeHub.finance.domain.FinanceChartData
import java.util.Locale

/** Native Canvas charts; no network, chart SDK or fabricated sample transactions. */
class FinanceChartView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var data: FinanceChartData? = null
    private var trend = false
    private val palette = intArrayOf(0xff287d71.toInt(),0xff8580bc.toInt(),0xffce9177.toInt(),0xff6ca7bc.toInt(),0xffb7aa6a.toInt(),0xff9baaaf.toInt())
    fun setData(value: FinanceChartData, showTrend: Boolean) {
        data=value; trend=showTrend
        contentDescription = if(trend) buildString {
            append(context.getString(if(value.yearly) R.string.finance_year_trend_hint else R.string.finance_trend_hint))
            value.dailyExpense.indices.filter { value.dailyExpense[it] != 0.0 || value.dailyIncome[it] != 0.0 }.forEach {
                append(String.format(Locale.getDefault(), "; %d: %s %.2f CNY, %s %.2f CNY", it+1,
                    context.getString(R.string.finance_income_short),value.dailyIncome[it],
                    context.getString(R.string.finance_spend_short),value.dailyExpense[it]))
            }
        } else context.getString(R.string.finance_distribution)
        invalidate()
    }
    private fun dp(value: Float)=value*resources.displayMetrics.density
    private fun label(canvas: Canvas, text: String, x: Float, y: Float, size: Float=12f) {
        paint.style=Paint.Style.FILL; paint.color=context.getColor(R.color.text_secondary); paint.textSize=dp(size); paint.textAlign=Paint.Align.CENTER
        canvas.drawText(text,x,y,paint)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d=data ?: return
        if (d.expense+d.income == 0.0 || (!trend && d.categories.isEmpty())) {
            label(canvas,context.getString(R.string.finance_chart_empty),width/2f,height/2f); return
        }
        if(trend) {
            val base=height-dp(24f); val top=dp(20f); val left=dp(12f); val space=(width-2*left)/d.dailyExpense.size
            val max=maxOf(d.dailyExpense.maxOrNull() ?: 0.0,d.dailyIncome.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
            paint.style=Paint.Style.STROKE; paint.strokeWidth=dp(1f); paint.color=context.getColor(R.color.brand_teal_light)
            for(i in 0..3) { val y=top+(base-top)*i/3; canvas.drawLine(left,y,width-left,y,paint) }
            paint.style=Paint.Style.FILL
            for(i in d.dailyExpense.indices) {
                val x=left+i*space
                paint.color=0xff287d71.toInt()
                canvas.drawRect(x,base-((base-top)*d.dailyIncome[i]/max).toFloat(),x+space*.38f,base,paint)
                paint.color=0xffc56c52.toInt()
                canvas.drawRect(x+space*.43f,base-((base-top)*d.dailyExpense[i]/max).toFloat(),x+space*.81f,base,paint)
            }
            label(canvas,"1",left+space/2,height-dp(5f));label(canvas,if(d.yearly) "6" else "15",left+(if(d.yearly) 5.5f else 14.5f)*space,height-dp(5f));label(canvas,d.dailyExpense.size.toString(),width-left-space/2,height-dp(5f))
            label(canvas,context.getString(R.string.chart_max,String.format(Locale.getDefault(),"%.2f",max)),width/2f,dp(12f),10f)
        } else {
            val radius=minOf(width,height)*.36f; val x=width/2f; val y=height/2f
            val rect=RectF(x-radius,y-radius,x+radius,y+radius)
            val total=d.categories.values.sum(); var start=-90f
            paint.style=Paint.Style.STROKE;paint.strokeWidth=dp(24f);paint.strokeCap=Paint.Cap.BUTT
            d.categories.values.forEachIndexed { index,value ->
                paint.color=palette[index%palette.size];val angle=(value/total*360).toFloat()
                canvas.drawArc(rect,start,angle,false,paint);start+=angle
            }
            label(canvas,"¥ " + com.lifeHub.finance.domain.Money.format(d.categoriesMinor.values.fold(0L) { sum, value -> Math.addExact(sum,value) }),x,y+dp(5f),20f)
        }
    }
}
