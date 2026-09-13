package cn.hospital.eph.prescription.pdf;

import cn.hospital.eph.prescription.entity.Prescription;
import cn.hospital.eph.prescription.entity.PrescriptionDiagnosis;
import cn.hospital.eph.prescription.entity.PrescriptionItem;
import cn.hospital.eph.prescription.entity.SignatureRecord;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

/** OpenPDF 渲染处方版式（不含数字签，数字签由 PdfService 二次加盖） */
@Component
public class PdfRenderer {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public byte[] render(Prescription rx,
                         java.util.List<PrescriptionItem> items,
                         java.util.List<PrescriptionDiagnosis> diagnoses,
                         SignatureRecord doctorSig,
                         SignatureRecord pharmacistSig) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 36, 36, 42, 36);
            PdfWriter.getInstance(doc, out);
            doc.open();

            BaseFont bf = buildChineseBaseFont();
            Font title = new Font(bf, 18, Font.BOLD);
            Font h = new Font(bf, 11, Font.BOLD);
            Font normal = new Font(bf, 10, Font.NORMAL);
            Font small = new Font(bf, 8, Font.NORMAL, Color.GRAY);

            Paragraph p = new Paragraph("互联网医院电子处方", title);
            p.setAlignment(Element.ALIGN_CENTER);
            p.setSpacingAfter(6f);
            doc.add(p);

            Paragraph no = new Paragraph("处方编号：" + rx.getRxNo() + "    版本：v" + rx.getRxVersion()
                    + "    类别：" + categoryName(rx.getRxCategory()), small);
            no.setAlignment(Element.ALIGN_CENTER);
            no.setSpacingAfter(10f);
            doc.add(no);

            PdfPTable meta = new PdfPTable(2);
            meta.setWidthPercentage(100);
            addCell(meta, "患者：" + rx.getPatientName()
                    + "（" + gender(rx.getPatientGender()) + "，" + rx.getPatientAge() + "岁）"
                    + (rx.getPatientIdCardMask() == null ? "" : " " + rx.getPatientIdCardMask()), normal, false);
            addCell(meta, "开方医生：" + rx.getDoctorName() + "（" + nz(rx.getDeptName()) + "）", normal, false);
            doc.add(meta);

            Paragraph dxTitle = new Paragraph("临床诊断：", h);
            dxTitle.setSpacingBefore(8f);
            doc.add(dxTitle);
            StringBuilder dx = new StringBuilder();
            for (PrescriptionDiagnosis d : diagnoses) {
                if (!dx.isEmpty()) dx.append("；");
                dx.append(d.getDiagnosisName());
                if (d.getIcd10Code() != null && !d.getIcd10Code().isBlank()) {
                    dx.append("（").append(d.getIcd10Code()).append("）");
                }
            }
            doc.add(new Paragraph(dx.toString(), normal));

            Paragraph rp = new Paragraph("Rp：", h);
            rp.setSpacingBefore(8f);
            doc.add(rp);

            PdfPTable table = new PdfPTable(new float[]{0.5f, 2.4f, 1.2f, 1.6f, 0.9f, 1.4f, 1f});
            table.setWidthPercentage(100);
            for (String head : new String[]{"序号", "药品名称/规格", "数量", "用法用量", "频次", "给药途径", "天数"}) {
                addCell(table, head, h, true);
            }
            int seq = 1;
            for (PrescriptionItem it : items) {
                addCell(table, String.valueOf(seq++), normal, true);
                addCell(table, it.getDrugName() + "\n" + nz(it.getSpec()) + " " + nz(it.getDosageForm()), normal, false);
                addCell(table, it.getQty().stripTrailingZeros().toPlainString() + " " + nz(it.getUnit()), normal, true);
                addCell(table, nz(it.getSingleDose()) + " " + nz(it.getDoseUnit())
                        + (it.getSkinTestFlag() != null && it.getSkinTestFlag() == 1 ? "（需皮试）" : ""), normal, false);
                addCell(table, nz(it.getFrequency()), normal, true);
                addCell(table, nz(it.getAdministrationRoute()), normal, true);
                addCell(table, it.getDays() == null ? "" : it.getDays() + "天", normal, true);
            }
            doc.add(table);

            Paragraph note = new Paragraph("注：本处方经医生、药师双数字签名后生效，请凭处方在有效期内购药。", small);
            note.setSpacingBefore(8f);
            doc.add(note);

            PdfPTable sign = new PdfPTable(2);
            sign.setWidthPercentage(100);
            sign.setSpacingBefore(16f);
            PdfPCell c1 = new PdfPCell(signatureBlock("开方医生", doctorSig, normal, small));
            c1.setPadding(4f);
            PdfPCell c2 = new PdfPCell(signatureBlock("审核药师", pharmacistSig, normal, small));
            c2.setPadding(4f);
            sign.addCell(c1);
            sign.addCell(c2);
            doc.add(sign);

            Paragraph verify = new Paragraph("数字签名验签：POST /api/verify/signatures/" + rx.getRxNo()
                    + "\nPDF 摘要 SHA-256（生成后回写）", small);
            verify.setSpacingBefore(12f);
            doc.add(verify);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("处方 PDF 渲染失败", e);
        }
    }

    private BaseFont buildChineseBaseFont() throws Exception {
        return BaseFont.createFont("fonts/wqy-microhei.ttc,0", BaseFont.IDENTITY_H,
                BaseFont.EMBEDDED, true, null, null);
    }

    private void addCell(PdfPTable table, String text, Font font, boolean center) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text, font));
        c.setPadding(4f);
        if (center) c.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(c);
    }

    private Phrase signatureBlock(String title, SignatureRecord sig, Font normal, Font small) {
        Phrase ph = new Phrase();
        ph.add(new com.lowagie.text.Chunk(title + "：\n", normal));
        if (sig != null) {
            ph.add(new com.lowagie.text.Chunk(
                    sig.getSignerName() + "\n"
                            + "证书：" + sig.getCertSerial() + "\n"
                            + "时间：" + sig.getSignTime().format(DT) + "\n"
                            + "数字签名：SHA256withRSA（PAdES，见 PDF 签名字典）\n",
                    small));
        } else {
            ph.add(new com.lowagie.text.Chunk("（未签名）\n", small));
        }
        return ph;
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }

    private String gender(Integer g) {
        if (g == null) return "未知";
        return g == 1 ? "男" : g == 2 ? "女" : "未知";
    }

    private String categoryName(Integer c) {
        return switch (c == null ? 1 : c) {
            case 2 -> "急诊处方";
            case 3 -> "儿科处方";
            case 4 -> "麻精药品处方";
            default -> "普通处方";
        };
    }
}
