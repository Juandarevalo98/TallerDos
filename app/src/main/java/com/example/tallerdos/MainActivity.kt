package com.example.tallerdos

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.view.Gravity
import android.widget.Button
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.FirebaseDatabase

class MainActivity : AppCompatActivity() {

    private var player1Position = 0
    private var player2Position = 0
    private var player1SixCount = 0 // Contador de "6" para el jugador 1
    private var player2SixCount = 0 // Contador de "6" para el jugador 2
    private val columns = 8

    private val cellPlayers = mutableMapOf<Int, MutableList<Int>>() // Almacena los jugadores en cada celda
    private val cellSpecialColors = mutableMapOf<Int, Int>() // Colores especiales de las casillas
    private val baseCellColor = Color.parseColor("#D3D3D3") // Color base de las celdas

    private val escaleras = mapOf(
        Pair(8, 39) to Color.parseColor("#556e42"),
        Pair(12, 38) to Color.parseColor("#81bf51"),
        Pair(32, 47) to Color.parseColor("#6bee04"),
        Pair(44, 61) to Color.parseColor("#539321")
    )

    private val serpientes = mapOf(
        Pair(31, 3) to Color.parseColor("#952c2c"),
        Pair(41, 22) to Color.parseColor("#e68e8e"),
        Pair(51, 5) to Color.parseColor("#fa0606"),
        Pair(60, 43) to Color.parseColor("#c44e4e")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_board)

        val database = FirebaseDatabase.getInstance()
        val gameRef = database.getReference("games").child("gameId123")

        val initialGameState = mapOf(
            "player1" to mapOf(
                "position" to 0,
                "sixCount" to 0
            ),
            "player2" to mapOf(
                "position" to 0,
                "sixCount" to 0
            ),
            "currentTurn" to 1
        )

        gameRef.setValue(initialGameState).addOnSuccessListener {
            // Datos iniciales establecidos
        }.addOnFailureListener {
            // Manejar errores
        }

        val boardGrid = findViewById<GridLayout>(R.id.boardGrid)
        val btnPlayer1 = findViewById<Button>(R.id.btnPlayer1)
        val btnPlayer2 = findViewById<Button>(R.id.btnPlayer2)
        val tvDice1 = findViewById<TextView>(R.id.tvDice1)
        val tvDice2 = findViewById<TextView>(R.id.tvDice2)

        var cellNumber = 1

        for (row in 0 until 8) {
            val isReverseRow = row % 2 != 0

            for (col in 0 until columns) {
                val actualColumn = if (isReverseRow) columns - 1 - col else col

                val cell = TextView(this).apply {
                    text = cellNumber.toString()
                    textSize = 28f
                    gravity = Gravity.CENTER
                    setBackgroundResource(R.drawable.cell_border) // Borde definido
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = 0 // Tamaño proporcional
                        height = 0 // Tamaño proporcional
                        columnSpec = GridLayout.spec(actualColumn, 1f)
                        rowSpec = GridLayout.spec(7 - row, 1f)
                    }

                    escaleras.forEach { (rango, color) ->
                        val (inicio, fin) = rango
                        if (cellNumber == inicio || cellNumber == fin) {
                            setBackgroundColor(color)
                            cellSpecialColors[cellNumber] = color
                        }
                    }

                    serpientes.forEach { (rango, color) ->
                        val (inicio, fin) = rango
                        if (cellNumber == inicio || cellNumber == fin) {
                            setBackgroundColor(color)
                            cellSpecialColors[cellNumber] = color
                        }
                    }

                    if (cellSpecialColors[cellNumber] == null) {
                        setBackgroundColor(baseCellColor)
                    }
                }

                boardGrid.addView(cell)
                cellPlayers[cellNumber] = mutableListOf()
                cellNumber++
            }
        }

        fun updatePlayerPosition(player: Int, newPosition: Int) {
            val previousPosition = if (player == 1) player1Position else player2Position
            cellPlayers[previousPosition]?.remove(player) // Eliminar del lugar anterior

            var finalPosition = newPosition
            var message = ""

            escaleras.forEach { (rango, _) ->
                val (inicio, fin) = rango
                if (newPosition == inicio) {
                    finalPosition = fin
                    message = "¡Subes por la escalera hasta la casilla $finalPosition!"
                }
            }

            serpientes.forEach { (rango, _) ->
                val (inicio, fin) = rango
                if (newPosition == inicio) {
                    finalPosition = fin
                    message = "¡Bajas por la serpiente hasta la casilla $finalPosition!"
                }
            }

            if (player == 1) {
                player1Position = finalPosition
            } else {
                player2Position = finalPosition
            }
            cellPlayers[finalPosition]?.add(player)

            if (message.isNotEmpty()) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }

        fun updateBoard() {
            for (i in 0 until boardGrid.childCount) {
                val cell = boardGrid.getChildAt(i) as TextView
                val cellNumber = i + 1

                cell.setBackgroundColor(cellSpecialColors[cellNumber] ?: baseCellColor)

                // Mostrar jugadores
                val playersInCell = cellPlayers[cellNumber] ?: listOf()
                if (playersInCell.isNotEmpty()) {
                    val colors = playersInCell.joinToString("") { player ->
                        if (player == 1) "🔵" else "🟠"
                    }
                    cell.text = "$cellNumber $colors"
                } else {
                    cell.text = cellNumber.toString()
                }
            }
        }

        fun animatePlayerMovement(player: Int, startPosition: Int, endPosition: Int, onFinished: () -> Unit) {
            val handler = Handler()
            var currentPosition = startPosition

            val runnable = object : Runnable {
                override fun run() {
                    cellPlayers[currentPosition]?.remove(player) // Eliminar jugador de la posición actual
                    updateBoard()

                    if (currentPosition < endPosition) {
                        currentPosition++
                        cellPlayers[currentPosition]?.add(player) // Agregar jugador a la siguiente posición
                        updateBoard()
                        handler.postDelayed(this, 300)
                    } else {
                        onFinished()
                    }
                }
            }

            handler.post(runnable)
        }

        fun animateBothDice(
            dice1TextView: TextView,
            dice2TextView: TextView,
            onAnimationEnd: (Int) -> Unit
        ) {
            val handler = Handler()
            val animationDuration = 1000L // Duración total de la animación
            val interval = 100L // Intervalo entre cada cambio de valor
            val startTime = System.currentTimeMillis()

            var dice1Result = 0
            var dice2Result = 0
            var dice1Finished = false
            var dice2Finished = false

            val runnableDice1 = object : Runnable {
                override fun run() {
                    val elapsedTime = System.currentTimeMillis() - startTime
                    if (elapsedTime < animationDuration) {
                        dice1TextView.text = (1..6).random().toString()
                        handler.postDelayed(this, interval)
                    } else {
                        dice1Result = (1..6).random()
                        dice1TextView.text = dice1Result.toString()
                        dice1Finished = true
                        if (dice1Finished && dice2Finished) {
                            onAnimationEnd(dice1Result + dice2Result)
                        }
                    }
                }
            }

            val runnableDice2 = object : Runnable {
                override fun run() {
                    val elapsedTime = System.currentTimeMillis() - startTime
                    if (elapsedTime < animationDuration) {
                        dice2TextView.text = (1..6).random().toString()
                        handler.postDelayed(this, interval)
                    } else {
                        dice2Result = (1..6).random()
                        dice2TextView.text = dice2Result.toString()
                        dice2Finished = true
                        if (dice1Finished && dice2Finished) {
                            onAnimationEnd(dice1Result + dice2Result)
                        }
                    }
                }
            }

            handler.post(runnableDice1)
            handler.post(runnableDice2)
        }

        fun resetPlayerToStart(player: Int) {
            val previousPosition = if (player == 1) player1Position else player2Position

            cellPlayers[previousPosition]?.remove(player)

            // Mostrar mensaje
            Toast.makeText(
                this,
                "Jugador $player obtiene tres 6 y vuelve al inicio",
                Toast.LENGTH_LONG
            ).show()

            // Actualizar la posición y el contador
            if (player == 1) {
                player1Position = 0
                player1SixCount = 0
            } else {
                player2Position = 0
                player2SixCount = 0
            }

            // Mover al jugador a la casilla inicial
            cellPlayers[0]?.add(player)

            // Actualizar visualmente el tablero
            updateBoard()
        }

        fun handlePlayerTurn(
            player: Int,
            currentPosition: Int,
            dice1TextView: TextView,
            dice2TextView: TextView,
            onTurnEnd: () -> Unit
        ) {
            animateBothDice(dice1TextView, dice2TextView) { diceSum ->
                // Obtenemos los valores actuales de los dados
                val dice1Result = dice1TextView.text.toString().toInt()
                val dice2Result = dice2TextView.text.toString().toInt()

                // Verificar si el jugador obtuvo un 6 en cualquiera de los dados
                if (dice1Result == 6 || dice2Result == 6) {
                    if (player == 1) {
                        player1SixCount++
                        if (player1SixCount == 3) {
                            resetPlayerToStart(1)
                            onTurnEnd()
                            return@animateBothDice
                        }
                    } else {
                        player2SixCount++
                        if (player2SixCount == 3) {
                            resetPlayerToStart(2)
                            onTurnEnd()
                            return@animateBothDice
                        }
                    }
                }

                // Calcular nueva posición del jugador
                val newPlayerPosition = currentPosition + diceSum

                if (newPlayerPosition <= 64) {
                    Toast.makeText(this, "Jugador $player avanza $diceSum posiciones", Toast.LENGTH_SHORT).show()
                    animatePlayerMovement(player, currentPosition, newPlayerPosition) {
                        updatePlayerPosition(player, newPlayerPosition)
                        updateBoard()

                        // Permitir un turno extra si saca 6 en los dados
                        if (diceSum == 6) {
                            Toast.makeText(this, "Jugador $player obtiene 6 y puede tirar nuevamente", Toast.LENGTH_SHORT).show()
                            if (player == 1) {
                                btnPlayer1.isEnabled = true
                                btnPlayer2.isEnabled = false
                            } else {
                                btnPlayer1.isEnabled = false
                                btnPlayer2.isEnabled = true
                            }
                        } else {
                            onTurnEnd()
                        }
                    }
                } else {
                    Toast.makeText(this, "Jugador $player no puede avanzar", Toast.LENGTH_SHORT).show()
                    onTurnEnd()
                }
            }
        }

        btnPlayer1.setOnClickListener {
            btnPlayer1.isEnabled = false
            btnPlayer2.isEnabled = false

            handlePlayerTurn(1, player1Position, tvDice1, tvDice2) {
                btnPlayer1.isEnabled = false
                btnPlayer2.isEnabled = true
            }
        }

        btnPlayer2.setOnClickListener {
            btnPlayer1.isEnabled = false
            btnPlayer2.isEnabled = false

            handlePlayerTurn(2, player2Position, tvDice1, tvDice2) {
                btnPlayer1.isEnabled = true
                btnPlayer2.isEnabled = false
            }
        }

        updateBoard()
    }
}