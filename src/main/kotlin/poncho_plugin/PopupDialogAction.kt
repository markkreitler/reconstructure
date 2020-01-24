package poncho_plugin

import com.intellij.notification.NotificationDisplayType.BALLOON
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.SelectionModel
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection


class PopupDialogAction : AnAction() {
    override fun update(e: AnActionEvent) {
        // Set the availability based on whether a project is open.
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        println("Starting plugin...")
        parseSelectedText(event)
    }

    private fun parseSelectedText(event: AnActionEvent) {
        val project = event.project
        var message : String = "No project loaded!"
//        var testData = "OrderRequest(items=[OrderItemRequest(menuItemId=11002, name=Grilled Caesar Chicken, shortDescription=grilled chicken, romaine lettuce, caesar dressing, tomato, parmesan crisp, price=795, imageUrl=https://storage.googleapis.com/poncho-staging-storage/54_1554841653161_Caesar_Rev2.png, customizations=[], isComped=false)], combos=[], orderType=TO_GO, customerId=13275, menuId=50, cardData=CardData(ksn=9011880B49277E000189, magnePrint=4B968BC028667659B5B86CCBB61DD7F0B0101DA1BAB746C0F63305BDAA50F1FD2DDE462A09FC6172AE91DEDC8BE5BFE45722F5C379FF3A21, magnePrintStatus=61401000, track2=EBA8069D3E1291659BC7CBA954CE30DB0A4133D1197340680BA6AB78DE7ACCFDD864C8D5A20991C0, deviceSN=B49277E040319AA, cardHolderName=KREITLER/MARK , cardPAN=4147000010000160), cardId=vbWMVN+Tr/H+xaYnqQ35epH4QUEAT4+eQC9kpXoW+Vo=, promotionCode=null, paymentType=null, guestCheckout=null, totalDiscount=0, orderSubtotal=795, totalTax=64, orderTotal=859, orderId=null, compRequest=null)"

        project?.also { _ ->
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            val contents = clipboard.getContents(null)
            message = if (contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                val stringData = contents.getTransferData(DataFlavor.stringFlavor) as String
                val parser = DataParser(stringData)
                parser.parseStructures()
            }
            else {
                "Please select a Watch variable and perform 'Copy Value' first."
            }
        }

        val dialog = NotificationGroup("poncho_plugin", BALLOON, true)
        dialog.createNotification("Selection",
            message,
            NotificationType.INFORMATION,
            null
        ).notify(event.project)
    }
}

