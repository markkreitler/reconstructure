package poncho_plugin

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

class DataParser(private val inText: String) {
    class StackElement(val fn : (String, Boolean) -> Unit, depth : Int) {
        var buf = ""
        var indent = ""
        var wasSubParsed = false
        var isListOrObject = false

        companion object {
            const val SPACES_PER_INDENT = "  "
        }

        init {
            for (i in 0..depth) {
                indent += SPACES_PER_INDENT
            }
        }
    }

    private val symbols = mutableMapOf<String, (String, Boolean) -> Unit>()

    init {
        symbols["["] = ::doList
        symbols["]"] = ::undoList
        symbols["("] = ::doObject
        symbols[")"] = ::undoObject
        symbols["="] = ::doValue
    }

    companion object {
        // TODO (KREITLER): generalize this to a table of symbols that must be replaced, parsed, and re-replaced.
        const val FAKE_COMMA = "~comma%"

        // TODO (KREITLER): generalize this to a table of fields that must be treated as strings even though they
        // appear to be strictly confined to another (numerical) data type.
        const val FAKE_CARD_ID = "~card_id%"

        const val SUCCESS_MESSAGE = "Structure copied to clipboard."
    }

    private var curChar = 0
    private val stateStack = mutableListOf<StackElement>()
    private var done = true
    private var err : String = ""
    private var buf = ""
    private var cardId = ""

    fun parseStructures() : String {
        var workText = replaceCardId(inText)
        workText = replaceFakeCommas(workText)

        stateStack.clear()
        push(::baseRead)
        curChar = 0
        done = false
        buf = ""

        parse(workText)

        workText = buf.replace(FAKE_COMMA, ",")
        workText = workText.replace(FAKE_CARD_ID, cardId)

        return when (err) {
            "" -> {
                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                clipboard.setContents(StringSelection(workText), null)
                SUCCESS_MESSAGE
            }
            else -> {
                "ERROR: $err"
            }
        }
    }

    private fun replaceCardId(inText : String) : String {
        val idIndex = inText.toLowerCase().indexOf("cardid")
        var out = inText

        if (idIndex >= 0) {
            val theRest = inText.substring(idIndex)
            val endIndex = Math.min(theRest.indexOf(','), theRest.indexOf(')'))
            val startIndex = idIndex + theRest.indexOf('=')

            if (startIndex >= 0 && endIndex >= 0 && startIndex < inText.length && endIndex < inText.length) {
                val pre = inText.substring(0, startIndex + 1) // everything up to 'cardId='
                val post = theRest.substring(endIndex) // everything after the card ID
                out = pre + FAKE_CARD_ID + post
                cardId = theRest.substring(0, endIndex) // The card Id
            }
        }

        return out
    }

    private fun replaceFakeCommas(inText : String) : String {
        var wantsFakeComma = false
        var out = ""

        for (char in inText) {
            when (char) {
                ',' -> {
                    if (wantsFakeComma) {
                        out += FAKE_COMMA
                    } else {
                        out += char
                    }

                    wantsFakeComma = true
                }

                '(', '[' -> {
                    wantsFakeComma = false
                    out += char
                }

                ')', ']' -> {
                    wantsFakeComma = true
                    out += char
                }

                '=' -> {
                    if (wantsFakeComma) {
                        val lastFakeComma = out.lastIndexOf(FAKE_COMMA)
                        if (lastFakeComma >= 0) {
                            val pre = out.substring(0, lastFakeComma)
                            val aft = out.substring(lastFakeComma + FAKE_COMMA.length)
                            out = "$pre, $aft"
                        }
                    }

                    wantsFakeComma = true
                    out += char
                }

                else -> {
                    out += char
                }
            }
        }

        return out
    }

    private fun push(newFn : (String, Boolean) -> Unit) {
        if (stateStack.size > 0) stateStack[0].wasSubParsed = true

        val newState = StackElement(newFn, stateStack.size)
        stateStack.add(0, newState)
    }

    private fun pop() {
        if (stateStack.size == 0) {
            onError("Stack stack underflow!")
        }
        else {
            if (stateStack.size == 1) {
                buf += stateStack[0].buf.trim()
            }
            else {
                stateStack[1].buf += stateStack[0].buf.trim()
            }

            stateStack.removeAt(0)
        }
    }

    private fun write(chr : Char, doClear : Boolean = false) {
        if (stateStack.size > 0) {
            if (doClear) {
                stateStack[0].buf = ""
            }

            stateStack[0].buf += chr
        }
        else {
            if (doClear) {
                buf = ""
            }

            buf += chr
        }
    }

    private fun write(str : String, doClear : Boolean = false) {
        if (stateStack.size > 0) {
            if (doClear) {
                stateStack[0].buf = ""
            }

            stateStack[0].buf += str
        }
        else {
            if (doClear) {
                buf = ""
            }

            buf += str
        }
    }

    private fun parse(inText : String) {
        while (!done) {
            val key = "${inText[curChar]}"

            symbols[key]?.also { fn ->
                fn(inText, true)
            } ?: stateStack[0].fn(inText, false)

            // 'done' condition could be modified by the states executed above
            // (particularly if an error occurs). We use the 'or' operator to
            // prevent overwriting such results.
            done = done or (curChar >= inText.length)
        }

        while (stateStack.size > 0) {
            pop()
        }
    }

    private fun buffer() : String {
        return if (stateStack.size > 0) stateStack[0].buf else buf
    }

    private fun indent(depth: Int = 0) : String {
        return if (stateStack.size > depth) stateStack[depth].indent else  ""
    }

    private fun finishValue(finisher : Char) {
        var newStr = buffer().substring(1).trim()
        val newStrLower = newStr.toLowerCase()
        val isBoolean = newStrLower == "false" || newStrLower == "true"
        val isNullLiteral = newStrLower.indexOf("null") >= 0
        val isNum = "^-?\\d+\\.?\\d*$".toRegex()
        var isCardData = false

        if (stateStack.size > 2 && stateStack[2].fn == ::doValue) {
            if (stateStack[2].buf.toLowerCase().indexOf("carddata") >= 0) {
                isCardData = true
            }
        }

        if (isCardData || (!isNullLiteral && !stateStack[0].isListOrObject && finisher == ',' && !isBoolean && !isNum.containsMatchIn(newStr))) {
            newStr = '"' + newStr + '"'
        }

        newStr = "=$newStr"

        if (finisher == ',') {
            newStr = "$newStr\n${indent()}"
        }

        write(newStr, true)
        pop()
    }

    // Parsers //////////////////////////////////////////////////////////////////
    private fun baseRead(inText : String, @Suppress("UNUSED_PARAMETER")isStart: Boolean) {
        val char = inText[curChar]
        if (char == ',') {
            write(inText[curChar++])
            write('\n' + indent())
        }
        else {
            write(inText[curChar++])
        }
    }

    private fun readString(inText : String, isStart : Boolean) {
        if (isStart) {
            write(inText[curChar++])
        }
        else {
            val char = inText[curChar]

            if (char == ',') {
                finishValue(',')
            }
            else {
                write(inText[curChar++])
            }
        }
    }

    private fun doList(inText : String, isStart : Boolean) {
        if (isStart) {
            if (stateStack.size > 0 && stateStack[0].fn == ::doValue) {
                stateStack[0].isListOrObject = true
            }

            push(::doList)
            ++curChar
        }
        else {
            baseRead(inText, false)
        }
    }

    private fun undoList(@Suppress("UNUSED_PARAMETER")inText : String, @Suppress("UNUSED_PARAMETER")isStart : Boolean) {
        if (stateStack[0].fn == ::doValue || stateStack[0].fn == ::readString) {
            finishValue(']')
        }

        val newStr = if ("\\S".toRegex().containsMatchIn(buffer())) {
            // Found a non-whitespace character.
            "listOf(\n${indent()}${buffer()}\n${indent(1)})"
        }
        else {
            // Found only whitespace.
            "emptyList()"
        }

        ++curChar

        write(newStr, true)
        pop()
    }

    private fun doObject(inText : String, isStart : Boolean) {
        if (isStart) {
            if (stateStack.size > 0 && stateStack[0].fn == ::doValue) {
                stateStack[0].isListOrObject = true
            }

            push(::doObject)
            ++curChar
        }
        else {
            baseRead(inText, false)
        }
    }

    private fun undoObject(@Suppress("UNUSED_PARAMETER") inText : String, @Suppress("UNUSED_PARAMETER") isStart : Boolean) {
        if (stateStack[0].fn == ::doValue || stateStack[0].fn == ::readString) {
            finishValue(')')
        }

        val curBuffer = buffer()
        val newStr = if ("\\S".toRegex().containsMatchIn(curBuffer)) {
            // Found a non-whitespace character.
            "(\n${indent()}${buffer()}\n${indent(1)})"
        }
        else {
            // Found only whitespace.
            "()"
        }

        ++curChar

        write(newStr, true)
        pop()
    }

    private fun doValue(inText : String, isStart : Boolean) {
        if (isStart) {
            if (buffer().toLowerCase().indexOf("cardid") >= 0) {
                push(::readString)
                readString(inText, true)
            }
            else {
                push(::doValue)
                write(inText[curChar++], true)
            }
        }
        else {
            val char = inText[curChar]

            if (char == ',') {
                finishValue(',')
            }
            else {
                write(inText[curChar++])
            }
        }
    }

    private fun onError(message : String) {
        err = message
        curChar = Int.MAX_VALUE
    }
}