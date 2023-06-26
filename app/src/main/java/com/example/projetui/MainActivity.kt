package com.example.projetui

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.ar.ArSceneView
import io.github.sceneview.ar.node.ArModelNode
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.projetui.game.Game
import com.example.projetui.game.Player


class MainActivity : AppCompatActivity() {

    //UI
    lateinit var sceneView : ArSceneView
    lateinit var playerText : TextView
    lateinit var cheatScoreText : EditText
    var dozenNode = ArModelNode()
    var unitNode = ArModelNode()

    //Calcul du score de triche
    val game: Game= Game()
    var lastId: Int = 0
    var indexCarte:Int = 10

    //Voice
    private var speechRecognizer:SpeechRecognizer?=null
    private var keepListening : Boolean = true
    private var btnRegister : Button? = null

    // Utilisation de l'API activity result qui permet d'ouvrir l'activité scan QRCode ET de récupérer un result dans la main Activity'
    //données du joueur
    private val resultLauncherGamer = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){ result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Data que l'on récupère de Scan QR Activity vers Main
            val data = result.data?.getStringExtra(ScanQrCodeActivity.QR_CODE_KEY).toString()
            Log.i("distribution","card update")
            val player:Player = game.players[0]
            player.addCard(data)
            updateIndexCarte(data)
            Log.i("distribution",player.toString())
        }
    }
    //données de la banque
    private val resultLauncherBank = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){ result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Data que l'on récupère de Scan QR Activity vers Main
            val data = result.data?.getStringExtra(ScanQrCodeActivity.QR_CODE_KEY).toString()
            updateIndexCarte(data)
            Log.i("banque","$indexCarte")
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //PARTIE RECONNAISSANCE VOCALE

        //check de l'accès au micro
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)!=
            PackageManager.PERMISSION_GRANTED){
            checkPermissions()
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        btnRegister = findViewById<Button>(R.id.btnRegister)
        val speechRecognizerIntent = setUpSpeechRecognizerIntent()

        speechRecognizer!!.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                //Toast.makeText(applicationContext, "ready for speech", Toast.LENGTH_SHORT).show()
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                Log.i("Error", "pas d'écoute")
                if (keepListening) speechRecognizer!!.startListening(speechRecognizerIntent)
            }

            override fun onResults(results: Bundle?) {

                if (results != null) {
                    val data = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (data != null && data.isNotEmpty()) {
                        val commande = data!![0]
                        handleCommand(commande)
                        Log.i("data", commande)
                    }
                }
                if (keepListening) speechRecognizer!!.startListening(speechRecognizerIntent)
                else {
                    Log.i("message", "fini")
                    speechRecognizer!!.stopListening()
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }  )

        btnRegister!!.setOnClickListener { speechRecognizer!!.startListening(speechRecognizerIntent) }

        //PARTIE UI

        sceneView = findViewById(R.id.sceneView)
        sceneView.planeRenderer.isVisible = false

        playerText = findViewById(R.id.Player)
        cheatScoreText = findViewById(R.id.cheatScoreText)
        cheatScoreText.setText("Waiting")


    }

    private fun handleCommand(command: String) {
        Log.i("commande:", command)
        when(command){
            "bonjour"-> {
                game.players.add(Player(id=lastId+1))
                lastId+=1
                Log.i("player:", game.players[0].toString())
                cheatScoreText.setText(game.players[0].cheatScore.toString())
            }

            "banque première carte"-> {
                    val intent = Intent(this, ScanQrCodeActivity ::class.java)
                    resultLauncherBank.launch(intent)
            }

            "banque deuxième carte"-> {
                val intent = Intent(this, ScanQrCodeActivity ::class.java)
                resultLauncherBank.launch(intent)
            }

            //première carte joueur
            "distribution"-> {
                val player:Player = game.players[0]?:Player(id=-1)
                player.clearHand()
                if (player.id>-1){
                    val intent = Intent(this, ScanQrCodeActivity ::class.java)
                    resultLauncherGamer.launch(intent)
                    Log.i("distribution",player.toString())
                }
            }

            "deuxième carte" -> {
                val player: Player = game.players[0] ?: Player(id = -1)
                if (player.id > -1) {
                    val intent = Intent(this, ScanQrCodeActivity::class.java)
                    resultLauncherGamer.launch(intent)
                    Log.i("distribution", player.toString())
                }
            }

            "je prends"->{ val player:Player = game.players[0]?:Player(id=-1)
                if (player.id>-1) player.setCheatScore("prendre", indexCarte)
                val intent = Intent(this, ScanQrCodeActivity ::class.java)
                resultLauncherGamer.launch(intent)
            }

            "je passe"-> { val player:Player = game.players[0]?:Player(id=-1)
                if (player.id>-1) player.setCheatScore("passer", indexCarte)
            }

            "score de triche" -> {
                onCallCheatScore()
            }

            "fin de la partie"-> keepListening = false
            else-> {Log.i("player","vide")
            }
        }

    }

    private fun onCallCheatScore() {

        sceneView.removeChild(dozenNode)
        sceneView.removeChild(unitNode)

        val player:Player = game.players[0]

        val cheatScore = player.cheatScore

        cheatScoreText.setText(cheatScore.toString())

        dozenNode = ArModelNode(followHitPosition = true).apply {
            loadModelGlbAsync(
                glbFileLocation = "models/3d_number_${cheatScore/10}.glb",
                centerOrigin = Float3(1.0f,1.0f,5.0f)
            )
        }
        unitNode = ArModelNode(followHitPosition = true).apply {
            loadModelGlbAsync(
                glbFileLocation = "models/3d_number_${cheatScore%10}.glb",
                centerOrigin = Float3(-1.0f,1.0f,5.0f)
            )
        }

        sceneView.addChild(dozenNode)
        sceneView.addChild(unitNode)

    }

    companion object{
        const val RecordAudioRequestCode = 1
    }

    private fun updateIndexCarte(card:String){
        when{
            card=="10"->indexCarte+=1
            (card.toInt() in 2..6)->indexCarte-=1
        }
    }
    private fun setUpSpeechRecognizerIntent(): Intent {
        val speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
    return speechRecognizerIntent
    }

    private fun checkPermissions() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            ActivityCompat.requestPermissions(
                this,arrayOf(Manifest.permission.RECORD_AUDIO),
                RecordAudioRequestCode
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RecordAudioRequestCode && grantResults.isNotEmpty()){
            Toast.makeText(this,"Permission Granted",Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer!!.destroy()
    }

}
