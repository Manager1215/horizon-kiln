// A structure piece Horizon doesn't have, from a Java mod (structures.rs put it in `kiln/generated/structure`): read
// the way Horizon reads its own and cached, so the template function finds it. Horizon's own pieces come first.
fn kiln_template(resources: &StructureResources, id: &str) {
    let id = if id.contains(':') {
        id.to_owned()
    } else {
        format!("minecraft:{id}")
    };
    if resources.templates.read().unwrap().contains_key(&id) {
        return;
    }
    let Some((namespace, path)) = id.split_once(':') else {
        return;
    };
    if resources.dir.join(namespace).join(format!("{path}.nbt")).is_file() {
        return;
    }
    let file = std::path::Path::new("kiln/generated/structure")
        .join(namespace)
        .join(format!("{path}.nbt"));
    let Ok(bytes) = std::fs::read(&file) else {
        return;
    };
    let mut raw = Vec::new();
    if std::io::Read::read_to_end(&mut flate2::read::GzDecoder::new(bytes.as_slice()), &mut raw).is_err() {
        return;
    }
    let Ok((named, _)) = nbt::read_named(&raw, Limits::templates()) else {
        return;
    };
    let mut template = StructureTemplate::from_nbt(&named.tag, resources.registry, &resources.transforms);
    template.location = id.as_str().into();
    resources
        .templates
        .write()
        .unwrap()
        .insert(id, Some(Arc::new(template)));
}
