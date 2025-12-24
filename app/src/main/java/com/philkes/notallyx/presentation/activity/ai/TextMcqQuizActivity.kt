package com.philkes.notallyx.presentation.activity.ai

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.philkes.notallyx.R
import com.philkes.notallyx.data.api.models.MCQ

class TextMcqQuizActivity : AppCompatActivity() {

    private lateinit var counterView: TextView
    private lateinit var difficultyView: TextView
    private lateinit var questionView: TextView
    private lateinit var optionButtons: List<MaterialButton>
    private lateinit var prevBtn: MaterialButton
    private lateinit var nextBtn: MaterialButton
    private lateinit var resultCard: MaterialCardView
    private lateinit var scoreText: TextView
    private lateinit var btnBack: MaterialButton
    private lateinit var btnShare: MaterialButton

    private var mcqList: List<MCQ> = emptyList()
    private var index = 0
    private var score = 0
    private var difficulty: String = "easy"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_text_mcq_quiz)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.Toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        counterView = findViewById(R.id.CounterText)
        difficultyView = findViewById(R.id.DifficultyText)
        questionView = findViewById(R.id.QuestionText)
        optionButtons =
            listOf(
                findViewById(R.id.OptionA),
                findViewById(R.id.OptionB),
                findViewById(R.id.OptionC),
                findViewById(R.id.OptionD),
            )
        prevBtn = findViewById(R.id.BtnPrev)
        nextBtn = findViewById(R.id.BtnNext)
        resultCard = findViewById(R.id.ResultCard)
        scoreText = findViewById(R.id.ScoreText)
        btnBack = findViewById(R.id.BtnBack)
        btnShare = findViewById(R.id.BtnShare)

        val json = intent.getStringExtra(EXTRA_MCQS_JSON)
        difficulty = intent.getStringExtra(EXTRA_DIFFICULTY) ?: "easy"
        if (json.isNullOrBlank()) {
            Toast.makeText(this, R.string.ai_error_generic, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        mcqList =
            runCatching {
                val type = object : TypeToken<List<MCQ>>() {}.type
                Gson().fromJson<List<MCQ>>(json, type)
            }.getOrElse { emptyList() }

        if (mcqList.isEmpty()) {
            Toast.makeText(this, "No questions", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        prevBtn.setOnClickListener {
            if (index > 0) {
                index--
                renderQuestion()
            }
        }
        nextBtn.setOnClickListener {
            if (index < mcqList.size - 1) {
                index++
                renderQuestion()
            } else {
                showResult()
            }
        }

        btnBack.setOnClickListener {
            finish()
        }
        btnShare.setOnClickListener {
            val total = mcqList.size
            val intent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "AI MCQ score: $score / $total")
                }
            startActivity(Intent.createChooser(intent, "Share score"))
        }

        renderQuestion()
    }

    private fun renderQuestion() {
        resultCard.visibility = android.view.View.GONE
        val mcq = mcqList[index]
        val total = mcqList.size
        counterView.text = "Question ${index + 1} / $total"
        difficultyView.text = difficulty.replaceFirstChar { it.uppercase() }
        questionView.text = mcq.question

        val options = mcq.options.entries.sortedBy { it.key }
        val defaultTextColor = Color.parseColor("#1A1A1A")
        val defaultBgColor = Color.WHITE
        val correctColor = ContextCompat.getColor(this, R.color.colorGreen)
        val incorrectColor = Color.parseColor("#E53935")
        var answered = false

        // Reset tất cả nút về trạng thái mặc định
        optionButtons.forEach { btn ->
            btn.isEnabled = true
            btn.isClickable = true
            btn.backgroundTintList = ColorStateList.valueOf(defaultBgColor)
            btn.setTextColor(defaultTextColor)
        }

        optionButtons.forEachIndexed { idx, btn ->
            if (idx < options.size) {
                val (k, v) = options[idx]
                btn.visibility = android.view.View.VISIBLE
                btn.text = "$k: $v"
                btn.setOnClickListener {
                    if (answered) return@setOnClickListener
                    answered = true
                    // Khóa các nút khác nhưng giữ nguyên hiển thị
                    optionButtons.forEach { it.isEnabled = false }

                    val isCorrect = k == mcq.answer
                    if (isCorrect) {
                        score++
                        btn.backgroundTintList = ColorStateList.valueOf(correctColor)
                        btn.setTextColor(Color.WHITE)
                    } else {
                        btn.backgroundTintList = ColorStateList.valueOf(incorrectColor)
                        btn.setTextColor(Color.WHITE)
                        // Tô xanh đáp án đúng
                        optionButtons
                            .firstOrNull { it.text.startsWith(mcq.answer) }
                            ?.let { correctBtn ->
                                correctBtn.backgroundTintList =
                                    ColorStateList.valueOf(correctColor)
                                correctBtn.setTextColor(Color.WHITE)
                            }
                    }
                }
            } else {
                btn.visibility = android.view.View.GONE
            }
        }

        prevBtn.isEnabled = index > 0
        nextBtn.text = if (index == total - 1) "Finish" else "Next"
    }

    private fun showResult() {
        val total = mcqList.size
        scoreText.text = "$score / $total"
        resultCard.visibility = android.view.View.VISIBLE
    }

    companion object {
        const val EXTRA_MCQS_JSON = "extra_mcqs_json"
        const val EXTRA_DIFFICULTY = "extra_difficulty"
    }
}

