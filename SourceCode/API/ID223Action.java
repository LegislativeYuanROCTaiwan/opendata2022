package API;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import com.google.gson.Gson;

import API.obj.ID223Obj;
import API.obj.ID223ObjList;
import au.com.bytecode.opencsv.CSVReader;
//
import org.apache.struts2.ServletActionContext;
import com.opensymphony.xwork2.ActionSupport;
import javax.servlet.http.HttpServletRequest;

//委員會發言名單
public class ID223Action extends ActionSupport {

	private static final long serialVersionUID = -6756806711022425967L;
	private final Logger log = Logger.getLogger(this.getClass());
	private static final HashSet<String> availableFileType = new HashSet<String>(
		Arrays.asList("csv", "json", "xml", "xls", "txt"));

	private String meetingDateS;
	private String meetingDateE;
	private String meetingRoom;
	private String meetingTypeName;
	private String jointCommittee;

	private String importFilePath = "/lyodw/odwsys/ODM_FT/223_CSV/223_CSV.csv";
	private String exportFilePath = "/lyodw/tomcat7/webapps/odw/source/";
	private String exportFileName = String.valueOf(System.currentTimeMillis());

	private String fileType;

	public String execute() throws Exception {
		System.out.println("ID223Action start!!");
		HttpServletRequest request;
		 request = ServletActionContext.getRequest();

		if (StringUtils.isBlank(fileType) || !availableFileType.contains(fileType.toLowerCase())) {
			return SUCCESS;
		}

		try {
			List<ID223Obj> resultList = new ArrayList<>();
			ID223Obj iD223Obj = new ID223Obj();
			ID223ObjList iD223ObjList = new ID223ObjList();
			Path path = Paths.get(importFilePath);
			InputStreamReader isr = new InputStreamReader(new FileInputStream(path.toFile()),
				StandardCharsets.UTF_8);
			BufferedReader br = new BufferedReader(isr);

			int row = 1;
			String readLine;
			while ((readLine = br.readLine()) != null) {
				if (row == 1) {
					row++;
					continue;
				}
				// ======== CSV file column
				// smeetingDate,meetingRoom,meetingTypeName,jointCommittee,meetingName,meetingContent,legislatorNameList
				String[] detail = ((String) readLine).split(",", -1);

				iD223Obj = new ID223Obj(detail[0], detail[1], detail[2], detail[3], detail[4], detail[5], detail[6]);

				// generate query result
				if ((StringUtils.isBlank(meetingDateS) || !getCDate(iD223Obj.getSmeetingDate().replace("/", "")).before(getCDate(meetingDateS))) && 
					(StringUtils.isBlank(meetingDateE) || !getCDate(iD223Obj.getSmeetingDate().replace("/", "")).after(getCDate(meetingDateE))) &&
					(StringUtils.isBlank(meetingRoom) || meetingRoom.equals(iD223Obj.getMeetingRoom())) &&
					(StringUtils.isBlank(meetingTypeName) || meetingTypeName.equals(iD223Obj.getMeetingTypeName())) &&
					(StringUtils.isBlank(jointCommittee) || jointCommittee.equals(iD223Obj.getJointCommittee()))) {
					resultList.add(iD223Obj);
					iD223ObjList.getId223ObjLst().addAll(resultList);
				}

			}
			
			br.close();
			isr.close();

			if (fileType.equalsIgnoreCase("csv")) {
				generateCsv(exportFilePath, exportFileName, ".csv", resultList);
			} else if (fileType.equalsIgnoreCase("txt")) {
				generateCsv(exportFilePath, exportFileName, ".txt", resultList);
			} else if (fileType.equalsIgnoreCase("xls")) {
				String csvFilePath = generateCsv(exportFilePath, exportFileName, ".csv", resultList);

				// Opening CSV Files
				Workbook wb = new HSSFWorkbook();
				CreationHelper helper = wb.getCreationHelper();
				Sheet sheet = wb.createSheet("new sheet");

				Path path_xls = Paths.get(csvFilePath);
				InputStreamReader isr_xls = new InputStreamReader(new FileInputStream(path_xls.toFile()),
					StandardCharsets.UTF_8);
				BufferedReader br_xls = new BufferedReader(isr_xls);
				
				String readLine_xls;
				int r = 0;
				while ((readLine_xls = br_xls.readLine()) != null) {
					Row rows = sheet.createRow((short) r++);
					
					String[] line = ((String) readLine_xls).split(",", -1);

					for (int i = 0; i < line.length; i++)
						rows.createCell(i).setCellValue(helper.createRichTextString(line[i]));

				}

				// Write the output to a file
				FileOutputStream fileOut = new FileOutputStream(exportFilePath + exportFileName + ".xls");
				wb.write(fileOut);
				fileOut.close();
				br_xls.close();

			} else if (fileType.equalsIgnoreCase("json")) {
				String finalResult = "{\"dataList\":" + new Gson().toJson(resultList) + "}";
				FileWriter fw = new FileWriter(exportFilePath + exportFileName + ".txt");
				fw.write(finalResult);
				fw.close();
				request.setAttribute("json",finalResult);
			} else if (fileType.equalsIgnoreCase("xml")) {
				// xml
				StringWriter sw = new StringWriter();
				JAXBContext jaxbContext = JAXBContext.newInstance(ID223ObjList.class);
				Marshaller jaxbMarshaller = jaxbContext.createMarshaller();

				jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

				// Marshal the employees list in file
				jaxbMarshaller.marshal(iD223ObjList, new File(exportFilePath + exportFileName + ".xml"));
			}
			request.setAttribute("exportFilePath",exportFilePath);
			request.setAttribute("exportFileName",exportFileName);
			request.setAttribute("fileType",fileType);
		} catch (Exception e) {
			e.printStackTrace();
			// errorMsg = e.getMessage();
			return "exception";
		}
		System.out.println("ID223Action SUCCESS!!");
		return SUCCESS;
	}

	public static String generateCsv(String fileLocation, String fileName, String fileSubName,
		List<ID223Obj> resultList)
		throws Exception {
		String csvFilePath = fileLocation + fileName + fileSubName;
		try {

			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(
				    new FileOutputStream((csvFilePath)), "UTF-8"));
			bw.write('\ufeff');
			bw.write(
				"smeetingDate,meetingRoom,meetingTypeName,jointCommittee,meetingName,meetingContent,legislatorNameList");

			for (ID223Obj result : resultList) {
				bw.newLine();
				bw.write(result.getSmeetingDate() + "," + result.getMeetingRoom() + "," + result.getMeetingTypeName()
					+ "," + result.getJointCommittee() + "," + result.getMeetingName() + ","
					+ result.getMeetingContent() + "," + result.getLegislatorNameList());
			}

			bw.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		return csvFilePath;
	}

	public static Date getCDate(String origDate) {
		try {
			if (origDate.length() != 7) {
				return null;
			}
			SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");

			return sdf.parse(Integer.parseInt(origDate.substring(0, 3)) + 1911 + origDate.substring(3));
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	public String getMeetingDateS() {
		return meetingDateS;
	}
	
	public String getMeetingDateE() {
		return meetingDateE;
	}
	
	public void setMeetingDateS(String meetingDateS) {
		this.meetingDateS = meetingDateS;
	}
	
	public void setMeetingDateE(String meetingDateE) {
		this.meetingDateE = meetingDateE;
	}

	public String getMeetingRoom() {
		return meetingRoom;
	}

	public String getMeetingTypeName() {
		return meetingTypeName;
	}

	public String getImportFilePath() {
		return importFilePath;
	}

	public String getExportFilePath() {
		return exportFilePath;
	}

	public String getExportFileName() {
		return exportFileName;
	}

	public String getFileType() {
		return fileType;
	}

	public void setMeetingRoom(String meetingRoom) {
		this.meetingRoom = meetingRoom;
	}

	public void setMeetingTypeName(String meetingTypeName) {
		this.meetingTypeName = meetingTypeName;
	}

	public String getJointCommittee() {
		return jointCommittee;
	}

	public void setJointCommittee(String jointCommittee) {
		this.jointCommittee = jointCommittee;
	}

	public void setImportFilePath(String importFilePath) {
		this.importFilePath = importFilePath;
	}

	public void setExportFilePath(String exportFilePath) {
		this.exportFilePath = exportFilePath;
	}

	public void setExportFileName(String exportFileName) {
		this.exportFileName = exportFileName;
	}

	public void setFileType(String fileType) {
		this.fileType = fileType;
	}

}
