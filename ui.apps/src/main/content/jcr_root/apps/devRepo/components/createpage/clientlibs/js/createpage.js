document.addEventListener("DOMContentLoaded", function () {
  const form = document.getElementById("createPageForm");
  if (!form) return;

  form.addEventListener("submit", function (e) {
    e.preventDefault();

    const formData = new FormData();
    formData.append("authKey", document.getElementById("authKey").value);
    formData.append("filePath", document.getElementById("filePath").value);

    fetch("/apps/test/createpage", {
      method: "POST",
      credentials: "same-origin",
      body: formData,
    })
      .then((response) => {
        if (!response.ok) throw new Error("HTTP " + response.status);
        return response.text();
      })
      .then((text) => {
        document.getElementById("responseText").innerText = text;
      })
      .catch((err) => {
        document.getElementById("responseText").innerText = "Error: " + err.message;
      });
  });
});