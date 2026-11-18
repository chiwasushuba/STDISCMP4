package edu.northstar.web;

import edu.northstar.contracts.*;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PortalController {
  public static final String TERM = "2026–2027 · Semester 1";
  private final PortalApi api;

  public PortalController(PortalApi api) {
    this.api = api;
  }

  private PortalSession user() {
    return (PortalSession) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
  }

  @GetMapping("/login")
  String loginPage() {
    return "login";
  }

  @PostMapping("/login")
  String login(
      @RequestParam String email,
      @RequestParam String password,
      HttpServletRequest request,
      HttpServletResponse response,
      Model model) {
    try {
      PortalSession session = new PortalSession(api.login(email, password));
      request.getSession();
      request.changeSessionId();
      var context = SecurityContextHolder.createEmptyContext();
      context.setAuthentication(
          new UsernamePasswordAuthenticationToken(
              session, null, List.of(new SimpleGrantedAuthority("ROLE_" + session.getRole()))));
      SecurityContextHolder.setContext(context);
      new HttpSessionSecurityContextRepository().saveContext(context, request, response);
      return "redirect:/";
    } catch (StatusRuntimeException exception) {
      model.addAttribute("error", message(exception));
      model.addAttribute("email", email);
      return "login";
    }
  }

  @PostMapping("/signout")
  String signout(HttpServletRequest request) {
    boolean revoked = true;
    try {
      api.logout(user());
    } catch (StatusRuntimeException exception) {
      revoked = false;
    }
    request.getSession().invalidate();
    SecurityContextHolder.clearContext();
    return "redirect:/login?signedout" + (revoked ? "" : "&revocationPending");
  }

  @GetMapping("/")
  String dashboard(Model model) {
    if (user().getRole().equals("FACULTY")) return "redirect:/faculty";
    base(model, "dashboard", "Your semester, at a glance", "A little planning. A great semester.");
    loadCourses(model, "", "", false);
    loadGrades(model, "");
    return "portal";
  }

  @GetMapping("/courses")
  String courses(
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "") String department,
      Model model) {
    if (!user().getRole().equals("STUDENT")) return "redirect:/faculty";
    base(
        model,
        "courses",
        "Find your next course",
        "Explore the catalog and build a semester that works for you.");
    model.addAttribute("search", search);
    model.addAttribute("department", department);
    loadCourses(model, search, department, false);
    return "portal";
  }

  @GetMapping("/enrollment")
  String enrollment(Model model) {
    if (!user().getRole().equals("STUDENT")) return "redirect:/faculty";
    base(model, "enrollment", "My enrollment", "Your confirmed courses, all in one place.");
    loadCourses(model, "", "", true);
    return "portal";
  }

  @PostMapping("/enroll")
  String enroll(@RequestParam String courseId, RedirectAttributes flash) {
    try {
      flash.addFlashAttribute("success", api.enroll(user(), courseId).getMessage());
    } catch (StatusRuntimeException exception) {
      flash.addFlashAttribute("error", message(exception));
    }
    return "redirect:/enrollment";
  }

  @GetMapping("/grades")
  String grades(@RequestParam(defaultValue = "") String term, Model model) {
    if (!user().getRole().equals("STUDENT")) return "redirect:/faculty";
    base(model, "grades", "A record of your progress", "Review your published grades by semester.");
    // Fetch all owned grades once so filtering never removes semester choices.
    loadGrades(model, "");
    model.addAttribute("selectedTerm", term);
    if (model.getAttribute("grades") instanceof List<?> records) {
      model.addAttribute(
          "grades",
          records.stream()
              .map(Grade.class::cast)
              .filter(g -> term.isEmpty() || g.getTerm().equals(term))
              .toList());
    }
    return "portal";
  }

  @GetMapping("/faculty")
  String faculty(
      @RequestParam(defaultValue = "") String code,
      @RequestParam(defaultValue = "") String term,
      HttpSession session,
      Model model) {
    if (!user().getRole().equals("FACULTY")) return "redirect:/";
    base(
        model,
        "faculty",
        "Your teaching workspace",
        "Review your roster and publish grades with confidence.");
    try {
      var classes = api.classes(user()).getClassesList();
      model.addAttribute("classes", classes);
      TeachingClass selected =
          classes.stream()
              .filter(c -> c.getCourseCode().equals(code) && c.getTerm().equals(term))
              .findFirst()
              .orElse(classes.isEmpty() ? null : classes.getFirst());
      if (selected != null) {
        model.addAttribute("selectedClass", selected);
        model.addAttribute(
            "roster",
            api.roster(user(), selected.getCourseCode(), selected.getTerm()).getGradesList());
        Object pending = session.getAttribute("pendingGrades");
        if (pending instanceof GradeUpload upload
            && upload.getCourseCode().equals(selected.getCourseCode())
            && upload.getTerm().equals(selected.getTerm())) {
          model.addAttribute("preview", upload);
        }
      }
    } catch (StatusRuntimeException exception) {
      model.addAttribute("gradeError", message(exception));
    }
    return "portal";
  }

  @PostMapping("/faculty/preview")
  String preview(
      @RequestParam MultipartFile file,
      @RequestParam String code,
      @RequestParam String term,
      HttpSession session,
      RedirectAttributes flash) {
    session.removeAttribute("pendingGrades");
    try {
      if (file.isEmpty() || file.getSize() > 256 * 1024)
        throw new IllegalArgumentException("Choose a CSV file smaller than 256 KB.");
      GradeUpload upload =
          CsvGrades.parse(new String(file.getBytes(), StandardCharsets.UTF_8), code, term);
      // Validate class ownership before retaining an upload for confirmation.
      api.roster(user(), code, term);
      session.setAttribute("pendingGrades", upload);
    } catch (IllegalArgumentException | IOException exception) {
      flash.addFlashAttribute("error", exception.getMessage());
    } catch (StatusRuntimeException exception) {
      flash.addFlashAttribute("error", message(exception));
    }
    flash.addAttribute("code", code);
    flash.addAttribute("term", term);
    return "redirect:/faculty";
  }

  @PostMapping("/faculty/publish")
  String publish(HttpSession session, RedirectAttributes flash) {
    Object pending = session.getAttribute("pendingGrades");
    if (!(pending instanceof GradeUpload upload)) {
      flash.addFlashAttribute("error", "Preview a grade file before publishing.");
      return "redirect:/faculty";
    }
    try {
      flash.addFlashAttribute("success", api.upload(user(), upload).getMessage());
      session.removeAttribute("pendingGrades");
    } catch (StatusRuntimeException exception) {
      flash.addFlashAttribute("error", message(exception));
    }
    flash.addAttribute("code", upload.getCourseCode());
    flash.addAttribute("term", upload.getTerm());
    return "redirect:/faculty";
  }

  @PostMapping("/faculty/discard")
  String discard(HttpSession session) {
    session.removeAttribute("pendingGrades");
    return "redirect:/faculty";
  }

  @GetMapping("/faculty/template")
  ResponseEntity<String> template(@RequestParam String code, @RequestParam String term) {
    var roster = api.roster(user(), code, term).getGradesList();
    StringBuilder csv = new StringBuilder("student_id,grade\n");
    roster.forEach(
        grade ->
            csv.append(grade.getStudentId()).append(',').append(grade.getValue()).append('\n'));
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=grades.csv")
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .body(csv.toString());
  }

  private void base(Model model, String page, String title, String subtitle) {
    model.addAttribute("user", user());
    model.addAttribute("page", page);
    model.addAttribute("title", title);
    model.addAttribute("subtitle", subtitle);
    model.addAttribute("currentTerm", TERM);
  }

  private void loadCourses(Model model, String search, String department, boolean enrolledOnly) {
    try {
      List<Course> courses = api.courses(user(), search, department, enrolledOnly).getCoursesList();
      List<Course> enrolled = courses.stream().filter(Course::getEnrolled).toList();
      model.addAttribute("courses", courses);
      model.addAttribute("enrolled", enrolled);
      model.addAttribute("units", enrolled.stream().mapToInt(Course::getUnits).sum());
      model.addAttribute(
          "openCourses", courses.stream().filter(c -> c.getOpen() && c.getSeats() > 0).count());
    } catch (StatusRuntimeException exception) {
      model.addAttribute("courseError", message(exception));
    }
  }

  private void loadGrades(Model model, String term) {
    try {
      List<Grade> grades = api.grades(user(), term).getGradesList();
      model.addAttribute("grades", grades);
      model.addAttribute("terms", grades.stream().map(Grade::getTerm).distinct().toList());
    } catch (StatusRuntimeException exception) {
      model.addAttribute("gradeError", message(exception));
    }
  }

  static String message(StatusRuntimeException exception) {
    return switch (exception.getStatus().getCode()) {
      case UNAVAILABLE, DEADLINE_EXCEEDED, INTERNAL, UNKNOWN ->
          "This feature is temporarily unavailable. Please try again shortly.";
      default ->
          Optional.ofNullable(exception.getStatus().getDescription())
              .orElse("The request could not be completed.");
    };
  }

  @ExceptionHandler(StatusRuntimeException.class)
  ResponseEntity<String> rpcError(StatusRuntimeException exception) {
    HttpStatus status =
        exception.getStatus().getCode() == Status.Code.PERMISSION_DENIED
            ? HttpStatus.FORBIDDEN
            : HttpStatus.SERVICE_UNAVAILABLE;
    return ResponseEntity.status(status).contentType(MediaType.TEXT_PLAIN).body(message(exception));
  }
}
