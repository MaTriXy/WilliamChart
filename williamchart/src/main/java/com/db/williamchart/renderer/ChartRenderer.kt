package com.db.williamchart.renderer

import com.db.williamchart.ChartContract
import com.db.williamchart.Painter
import com.db.williamchart.animation.ChartAnimation
import com.db.williamchart.data.DataPoint
import com.db.williamchart.data.Label
import com.db.williamchart.extensions.limits
import com.db.williamchart.view.ChartView.Axis

class ChartRenderer(
    private val view: ChartContract.View,
    private val painter: Painter,
    private var animation: ChartAnimation
) : ChartContract.Renderer {

    private lateinit var data: List<DataPoint>

    private lateinit var axis: Axis

    private var innerFrameLeft: Float = -1f

    private var innerFrameTop: Float = -1f

    private var innerFrameRight: Float = -1f

    private var innerFrameBottom: Float = -1f

    private var labelsSize: Float = -1f

    private var isProcessed: Boolean = false

    internal var xPacked = false

    internal var yAtZero = false

    private val xLabels: List<Label> by lazy {
        data.map { Label(it.label, -1F, -1F) }
    }

    private val yLabels by lazy {
        val scale = findBorderValues(data, yAtZero)
        val scaleStep = (scale.max - scale.min) / defaultScaleNumberOfSteps

        List(defaultScaleNumberOfSteps + 1) {
            val scaleValue = scale.min + scaleStep * it
            Label(scaleValue.toString(), -1F, -1F)
        }
    }

    override fun preDraw(
        width: Int,
        height: Int,
        paddingLeft: Int,
        paddingTop: Int,
        paddingRight: Int,
        paddingBottom: Int,
        axis: Axis,
        labelsSize: Float
    ): Boolean {

        if (isProcessed) // Data already processed, proceed with drawing
            return true

        if (data.size <= 1)
            throw IllegalArgumentException("A chart needs more than one entry.")

        this.axis = axis
        this.labelsSize = labelsSize

        val frameLeft = paddingLeft.toFloat()
        val frameTop = paddingTop.toFloat()
        val frameRight = width - paddingRight.toFloat()
        val frameBottom = height - paddingBottom.toFloat()

        val paddings = negotiatePaddingsXY(measurePaddingsX(), measurePaddingsY())

        innerFrameLeft = frameLeft + paddings.left
        innerFrameTop = frameTop + paddings.top
        innerFrameRight = frameRight - paddings.right
        innerFrameBottom = frameBottom - paddings.bottom

        placeLabelsX(innerFrameLeft, innerFrameTop, innerFrameRight, innerFrameBottom)
        placeLabelsY(innerFrameLeft, innerFrameTop, innerFrameRight, innerFrameBottom)

        placeDataPoints(innerFrameTop, innerFrameBottom)

        animation.animateFrom(innerFrameBottom, data) { view.postInvalidate() }

        isProcessed = true

        return false
    }

    override fun draw() {

        if (axis == Axis.XY || axis == Axis.X) view.drawLabels(xLabels)
        if (axis == Axis.XY || axis == Axis.Y) view.drawLabels(yLabels)

        view.drawData(innerFrameLeft, innerFrameTop, innerFrameRight, innerFrameBottom, data)
    }

    override fun render(entries: HashMap<String, Float>) {
        add(entries)
        view.postInvalidate()
    }

    override fun anim(entries: HashMap<String, Float>, animation: ChartAnimation) {
        add(entries)
        this.animation = animation
        view.postInvalidate()
    }

    private fun add(entries: HashMap<String, Float>) {
        data = entries.map {
            DataPoint(
                label = it.key,
                value = it.value,
                screenPositionX = 0f,
                screenPositionY = 0f
            )
        }
    }

    private fun measurePaddingsX(): Paddings {
        return if (axis != Axis.XY && axis != Axis.X)
            Paddings(0F, 0F, 0F, 0F)
        else
            Paddings(0F, 0F, 0f, painter.measureLabelHeight(labelsSize))
    }

    private fun measurePaddingsY(): Paddings {

        return if (axis != Axis.XY && axis != Axis.Y) return Paddings(0F, 0F, 0F, 0F)
        else {
            val longestChartLabel = yLabels.maxBy { painter.measureLabelWidth(it.label, labelsSize) }
            Paddings(
                if (longestChartLabel != null) painter.measureLabelWidth(longestChartLabel.label, labelsSize) else 0F,
                painter.measureLabelHeight(labelsSize) / 2,
                0F,
                painter.measureLabelHeight(labelsSize) / 2
            )
        }
    }

    private fun placeLabelsX(
        chartLeft: Float,
        chartTop: Float,
        chartRight: Float,
        chartBottom: Float
    ) {

        val auxLeft: Float
        val auxRight: Float

        if (xPacked) { // Pack labels
            val entryWidth = (chartRight - chartLeft) / (xLabels.size)
            auxLeft = chartLeft + entryWidth / 2
            auxRight = chartRight - entryWidth / 2
        } else if (axis == Axis.XY || axis == Axis.X) {
            auxLeft = chartLeft + painter.measureLabelWidth(xLabels.first().label, labelsSize) / 2
            auxRight = chartRight - painter.measureLabelWidth(xLabels.last().label, labelsSize) / 2
        } else { // X not displayed
            auxLeft = chartLeft
            auxRight = chartRight
        }

        val stepX = (auxRight - auxLeft) / (xLabels.size - 1)
        xLabels.forEachIndexed { index, label ->
            label.x = auxLeft + stepX * index
            label.y = chartBottom + painter.measureLabelHeight(labelsSize)
        }
    }

    private fun placeLabelsY(
        chartLeft: Float,
        chartTop: Float,
        chartRight: Float,
        chartBottom: Float
    ) {

        val screenStep = (chartBottom - chartTop) / defaultScaleNumberOfSteps
        var screenCursor = chartBottom + painter.measureLabelHeight(labelsSize) / 2

        yLabels.forEach {
            it.x = chartLeft - painter.measureLabelWidth(it.label, labelsSize) / 2
            it.y = screenCursor
            screenCursor -= screenStep
        }
    }

    private fun placeDataPoints(
        frameTop: Float,
        frameBottom: Float
    ) {

        val scale = findBorderValues(data, yAtZero)

        data.forEachIndexed { index, entry ->
            entry.screenPositionX = xLabels[index].x
            entry.screenPositionY = frameBottom -
                ((frameBottom - frameTop) * (entry.value - scale.min) /
                    (scale.max - scale.min))
        }
    }

    private fun findBorderValues(entries: List<DataPoint>, startScaleAtZero: Boolean): Scale {
        val limits = entries.limits()
        return Scale(
            min = if (startScaleAtZero) 0F else limits.first,
            max = limits.second
        )
    }

    private fun negotiatePaddingsXY(paddingsX: Paddings, paddingsY: Paddings): Paddings {
        return Paddings(
            maxOf(paddingsX.left, paddingsY.left),
            maxOf(paddingsX.top, paddingsY.top),
            maxOf(paddingsX.right, paddingsY.right),
            maxOf(paddingsX.bottom, paddingsY.bottom)
        )
    }

    companion object {
        private const val defaultScaleNumberOfSteps = 3
    }
}

class Scale(val min: Float, val max: Float)

class Paddings(val left: Float, val top: Float, val right: Float, val bottom: Float)