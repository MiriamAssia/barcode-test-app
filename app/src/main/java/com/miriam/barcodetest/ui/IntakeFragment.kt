package com.miriam.barcodetest.ui

import android.app.DatePickerDialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.miriam.barcodetest.R
import com.miriam.barcodetest.data.Resource
import com.miriam.barcodetest.data.model.Item
import com.miriam.barcodetest.data.model.NewItem
import com.miriam.barcodetest.data.model.NewTransaction
import com.miriam.barcodetest.data.repository.BatchesRepository
import com.miriam.barcodetest.data.repository.ItemsRepository
import com.miriam.barcodetest.data.repository.TransactionsRepository
import com.miriam.barcodetest.databinding.DialogNewItemBinding
import com.miriam.barcodetest.databinding.FragmentIntakeBinding
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * מסך קליטת מלאי.
 *
 * הזרימה: מזהים פריט (סריקת ברקוד או הקלדת מק"ט) -> ממלאים תאריך תפוגה
 * וכמות -> "קלוט פריט למלאי". בפועל נוצרת (או נמצאת) אצווה, ואז נרשמת
 * תנועת 'in'. הכמות במלאי מתעדכנת אוטומטית ע"י הטריגר ב-DB, ולא נכתבת
 * ישירות מכאן - כך שהמלאי תמיד נגזר מיומן התנועות ולא סותר אותו.
 */
class IntakeFragment : Fragment() {

    private var _binding: FragmentIntakeBinding? = null
    private val binding get() = _binding!!

    private val itemsRepository = ItemsRepository()
    private val batchesRepository = BatchesRepository()
    private val transactionsRepository = TransactionsRepository()

    /** הפריט שזוהה. בלעדיו אי אפשר לקלוט - אין לאיזה פריט לשייך את המלאי. */
    private var selectedItem: Item? = null
    private var expiryDate: LocalDate? = null

    private val displayDateFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd/MM/yyyy")

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        val code = result.contents
        if (code != null) {
            _binding?.barcodeInput?.setText(code)
            lookupBarcode(code)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentIntakeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.scanCard.setOnClickListener { startScan() }

        binding.barcodeInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                lookupBarcode(binding.barcodeInput.text.toString())
                true
            } else {
                false
            }
        }

        // ברגע שמשנים את הברקוד, הפריט שזוהה קודם כבר לא רלוונטי - מאפסים
        // אותו כדי שלא ייקלט מלאי לפריט הלא נכון.
        binding.barcodeInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (selectedItem != null && s.toString().trim() != selectedItem?.barcode) {
                    selectedItem = null
                    _binding?.resultStrip?.visibility = View.GONE
                }
            }
        })

        binding.expiryField.setOnClickListener { showDatePicker() }
        binding.expiryClear.setOnClickListener {
            expiryDate = null
            updateExpiryText()
        }

        binding.quantityMinus.setOnClickListener { changeQuantity(-1.0) }
        binding.quantityPlus.setOnClickListener { changeQuantity(1.0) }

        binding.batchToggle.setOnClickListener { toggleBatchDetails() }

        binding.submitButton.setOnClickListener { submit() }

        updateExpiryText()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ======================= סריקה וזיהוי פריט =======================

    private fun startScan() {
        val options = ScanOptions()
            .setPrompt(getString(R.string.intake_scan_prompt))
            .setBeepEnabled(true)
            .setOrientationLocked(false)
        scanLauncher.launch(options)
    }

    private fun lookupBarcode(rawCode: String) {
        if (_binding == null) return
        val code = rawCode.trim()
        if (code.isEmpty()) {
            showResult(getString(R.string.intake_search_first), isOk = false)
            return
        }

        setLookingUp(true)
        hideStatus()

        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = itemsRepository.findItemByBarcode(code)) {
                is Resource.Success -> {
                    setLookingUp(false)
                    val item = result.data
                    if (item != null) {
                        onItemFound(item)
                    } else {
                        onItemNotFound(code)
                    }
                }
                is Resource.Error -> {
                    setLookingUp(false)
                    showResult(result.message, isOk = false)
                }
                is Resource.Loading -> Unit
            }
        }
    }

    private fun onItemFound(item: Item) {
        selectedItem = item
        showResult("${item.name} (${item.unit})", isOk = true)
    }

    private fun onItemNotFound(barcode: String) {
        selectedItem = null
        showResult(getString(R.string.intake_item_not_found), isOk = false)
        showNewItemDialog(barcode)
    }

    // ======================= יצירת פריט חדש =======================

    private fun showNewItemDialog(barcode: String) {
        val dialogBinding = DialogNewItemBinding.inflate(layoutInflater)
        dialogBinding.newItemBarcode.text = barcode

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.new_item_title)
            .setMessage(R.string.intake_create_item_question)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.new_item_create, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        // מחליפים את המאזין של הכפתור החיובי אחרי ההצגה, כדי שהדיאלוג לא
        // ייסגר אוטומטית כשהוולידציה נכשלת.
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val name = dialogBinding.newItemName.text.toString().trim()
                if (name.isEmpty()) {
                    dialogBinding.newItemError.text = getString(R.string.new_item_name_required)
                    dialogBinding.newItemError.visibility = View.VISIBLE
                    return@setOnClickListener
                }
                dialogBinding.newItemError.visibility = View.GONE

                val newItem = NewItem(
                    name = name,
                    category = dialogBinding.newItemCategory.text.toString().trim()
                        .takeIf { it.isNotEmpty() },
                    unit = dialogBinding.newItemUnit.text.toString().trim()
                        .takeIf { it.isNotEmpty() } ?: "יחידה",
                    minQuantity = dialogBinding.newItemMinQuantity.text.toString().toDoubleOrNull() ?: 0.0,
                    barcode = barcode
                )

                viewLifecycleOwner.lifecycleScope.launch {
                    when (val result = itemsRepository.addItem(newItem)) {
                        is Resource.Success -> {
                            dialog.dismiss()
                            onItemFound(result.data)
                        }
                        is Resource.Error -> {
                            dialogBinding.newItemError.text = result.message
                            dialogBinding.newItemError.visibility = View.VISIBLE
                        }
                        is Resource.Loading -> Unit
                    }
                }
            }
        }

        dialog.show()
    }

    // ======================= תאריך תפוגה =======================

    private fun showDatePicker() {
        val initial = expiryDate ?: LocalDate.now().plusYears(1)
        DatePickerDialog(
            requireContext(),
            { _, year, monthZeroBased, dayOfMonth ->
                expiryDate = LocalDate.of(year, monthZeroBased + 1, dayOfMonth)
                updateExpiryText()
            },
            initial.year,
            initial.monthValue - 1,
            initial.dayOfMonth
        ).show()
    }

    private fun updateExpiryText() {
        val date = expiryDate
        if (date == null) {
            binding.expiryText.text = getString(R.string.intake_expiry_hint)
            binding.expiryText.setTextColor(color(R.color.md_on_surface_variant))
            binding.expiryClear.visibility = View.GONE
        } else {
            binding.expiryText.text = date.format(displayDateFormat)
            binding.expiryText.setTextColor(color(R.color.md_on_surface))
            binding.expiryClear.visibility = View.VISIBLE
        }
    }

    // ======================= כמות =======================

    private fun currentQuantity(): Double =
        binding.quantityInput.text.toString().toDoubleOrNull() ?: 0.0

    private fun changeQuantity(delta: Double) {
        val next = (currentQuantity() + delta).coerceAtLeast(0.0)
        binding.quantityInput.setText(formatQuantity(next))
        binding.quantityInput.setSelection(binding.quantityInput.text.length)
    }

    private fun formatQuantity(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    // ======================= פרטי אצווה =======================

    private fun toggleBatchDetails() {
        val expanded = binding.batchDetails.visibility == View.VISIBLE
        binding.batchDetails.visibility = if (expanded) View.GONE else View.VISIBLE
        binding.batchChevron.rotation = if (expanded) 0f else 180f
    }

    // ======================= קליטה למלאי =======================

    private fun submit() {
        val item = selectedItem
        if (item == null) {
            showStatus(getString(R.string.intake_search_first), isError = true)
            return
        }

        val quantity = currentQuantity()
        if (quantity <= 0.0) {
            showStatus(getString(R.string.intake_quantity_invalid), isError = true)
            return
        }

        setSubmitting(true)

        viewLifecycleOwner.lifecycleScope.launch {
            // שלב 1: איתור או יצירה של האצווה שאליה נכנס המלאי
            val batchResult = batchesRepository.findOrCreateBatch(
                itemId = item.id,
                batchNumber = binding.batchNumberInput.text.toString(),
                expiryDate = expiryDate?.toString(), // LocalDate.toString() = yyyy-MM-dd, בדיוק כמו date ב-Postgres
                supplier = binding.supplierInput.text.toString()
            )

            val batch = when (batchResult) {
                is Resource.Success -> batchResult.data
                is Resource.Error -> {
                    setSubmitting(false)
                    showStatus(batchResult.message, isError = true)
                    return@launch
                }
                is Resource.Loading -> {
                    setSubmitting(false)
                    return@launch
                }
            }

            // שלב 2: רישום תנועת הכניסה. הטריגר ב-DB יעדכן את כמות האצווה.
            val transaction = NewTransaction(
                type = "in",
                itemId = item.id,
                batchId = batch.id,
                quantity = quantity,
                reason = "קליטה"
            )

            when (val txResult = transactionsRepository.recordTransaction(transaction)) {
                is Resource.Success -> {
                    setSubmitting(false)
                    showStatus(
                        "נקלטו ${formatQuantity(quantity)} ${item.unit} של ${item.name}",
                        isError = false
                    )
                    resetForm()
                }
                is Resource.Error -> {
                    setSubmitting(false)
                    showStatus(txResult.message, isError = true)
                }
                is Resource.Loading -> setSubmitting(false)
            }
        }
    }

    private fun resetForm() {
        selectedItem = null
        expiryDate = null
        binding.barcodeInput.setText("")
        binding.batchNumberInput.setText("")
        binding.supplierInput.setText("")
        binding.quantityInput.setText("1")
        binding.resultStrip.visibility = View.GONE
        binding.batchDetails.visibility = View.GONE
        binding.batchChevron.rotation = 0f
        updateExpiryText()
    }

    // ======================= מצבי תצוגה =======================

    private fun setLookingUp(loading: Boolean) {
        binding.lookupProgress.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun setSubmitting(submitting: Boolean) {
        binding.submitProgress.visibility = if (submitting) View.VISIBLE else View.GONE
        binding.submitButton.isEnabled = !submitting
    }

    private fun showResult(message: String, isOk: Boolean) {
        binding.resultStrip.visibility = View.VISIBLE
        binding.resultStrip.setBackgroundResource(
            if (isOk) R.drawable.bg_result_ok else R.drawable.bg_result_error
        )
        binding.resultIcon.setImageResource(
            if (isOk) R.drawable.ic_check_circle else R.drawable.ic_error_outline
        )
        val tint = color(if (isOk) R.color.md_tertiary else R.color.md_error)
        binding.resultIcon.setColorFilter(tint)
        binding.resultText.text = message
        binding.resultText.setTextColor(tint)
    }

    private fun showStatus(message: String, isError: Boolean) {
        binding.statusText.visibility = View.VISIBLE
        binding.statusText.text = message
        binding.statusText.setTextColor(color(if (isError) R.color.md_error else R.color.md_tertiary))
    }

    private fun hideStatus() {
        binding.statusText.visibility = View.GONE
    }

    private fun color(resId: Int): Int = ContextCompat.getColor(requireContext(), resId)
}
